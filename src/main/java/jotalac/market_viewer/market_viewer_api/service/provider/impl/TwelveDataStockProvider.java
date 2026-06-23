package jotalac.market_viewer.market_viewer_api.service.provider.impl;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jotalac.market_viewer.market_viewer_api.dto.api_response.stock_api.StockGraphResponse;
import jotalac.market_viewer.market_viewer_api.dto.api_response.stock_api.StockPriceResponse;
import jotalac.market_viewer.market_viewer_api.entity.screens.AssetTimeFrame;
import jotalac.market_viewer.market_viewer_api.exception.api_provider.*;
import jotalac.market_viewer.market_viewer_api.service.provider.StockDataProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;

@Slf4j
@Component
public class TwelveDataStockProvider implements StockDataProvider {
    TwelveDataStockProvider(@Qualifier("twelveDataClient") RestClient restClient) {this.restClient = restClient;}

    private final RestClient restClient;

    //caching valid coin names
    private final Cache<String, Boolean> validSymbols = Caffeine.newBuilder()
            .maximumSize(100_000)
            .expireAfterWrite(Duration.ofDays(100))
            .build();

    private final ObjectMapper objectMapper = new ObjectMapper();


    @Override
    public StockPriceResponse fetchStockPriceData(String symbol, String apiKey) {
        JsonNode response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/quote")
                        .queryParam("symbol", symbol)
                        .queryParam("apikey", apiKey)
                        .build()
                )
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {
                    throw new ApiResponseError("TwelveData price - response error");
                })
                .body(JsonNode.class);

        if (!isResponseValid(response)) {
            log.warn(String.valueOf(response));
            throw new ApiResponseError("TwelveData price fetch error:" + response.get("message").asString());
        }

        return objectMapper.convertValue(response, StockPriceResponse.class);
    }

    @Override
    public List<Double> fetchStockGraphData(String symbol, AssetTimeFrame assetTimeFrame, String apiKey) {
        TwelveDataParams params = getTwelveDataParams(assetTimeFrame);

        JsonNode response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/time_series")
                        .queryParam("symbol", symbol)
                        .queryParam("interval", params.interval)
                        .queryParam("outputsize", params.outputSize)
                        .queryParam("apikey", apiKey)
                        .build()
                )
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {
                    throw new ApiResponseError("TwelveData graph fetch error");
                })
                .body(JsonNode.class);

        if (!isResponseValid(response)) {
            throw new ApiKeyNotValid("TwelveData api error: " + response.get("message").asString() );
        }
        StockGraphResponse graphData = objectMapper.convertValue(response, StockGraphResponse.class);

        return graphData.getReducedPrices();
    }

    @Override
    public void validateApiKey(String apiKey) {
        JsonNode response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api_usage")
                        .queryParam("apikey", apiKey)
                        .build()
                )
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {
                    throw new ApiKeyNotValid("TwelveData api key is not valid");
                })
                .body(JsonNode.class);

        if (!isResponseValid(response)) {
            throw new ApiKeyNotValid("TwelveData api key is not valid");
        }
    }

    @Override
    public void validateAssetSymbol(String symbol, String apiKey) {
        validSymbols.get(symbol, name -> {
            performSymbolValidation(symbol, apiKey);
            return true;
        });
    }

    private void performSymbolValidation(String symbol, String apiKey) {
        System.out.println("Validating symbol: " + symbol);
        JsonNode response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("price")
                        .queryParam("symbol", symbol)
                        .queryParam("apikey", apiKey)
                        .build())
                .retrieve()
                //handle any error
                .onStatus(HttpStatusCode::isError, (req, res) -> {

                    throw new AssetNameNotValid("Symbol " + symbol + " is not available");
                })
                .body(JsonNode.class);


        if (!isResponseValid(response)) {
            throw new AssetNameNotValid("Symbol '" + symbol + "' not available");
        }
    }

    // for some reason the twelve data api doesnt return the actual code in the response code but in the body :(
    private boolean isResponseValid(JsonNode response) {
        if (response == null) {
            return false;
        }
        // if error status is present in the response body
        JsonNode bodyResponseStatus = response.get("code");
        if (bodyResponseStatus != null) {
            if (bodyResponseStatus.asInt() == 429) {
                throw new ApiRateLimitExceededException("TwelveData api rate limit exceeded");
            }
            return false;
        }
        return true;
    }

    //handle time frames in stock provider
    @Override
    public boolean acceptsTimeFrame(AssetTimeFrame assetTimeFrame) {
        return switch (assetTimeFrame) {
            case FIVE_MINUTES, HOUR, FIVE_HOUR, DAY, WEEK, TWO_WEEKS, MONTH, YEAR, FIVE_YEARS -> true;
            default -> false;
        };
    }

    private record TwelveDataParams(String interval, int outputSize) {}

    private TwelveDataParams getTwelveDataParams(AssetTimeFrame timeFrame) {
        return switch (timeFrame) {
            case FIVE_MINUTES -> new TwelveDataParams("1min", 5);
            case HOUR -> new TwelveDataParams("1min", 60);
            case FIVE_HOUR -> new TwelveDataParams("1min", 300);
            case DAY -> new TwelveDataParams("5min", 78);
            case WEEK -> new TwelveDataParams("30min", 65);
            case TWO_WEEKS -> new TwelveDataParams("1h", 65);
            case MONTH -> new TwelveDataParams("2h", 70);
            case YEAR -> new TwelveDataParams("1day", 260);
            case FIVE_YEARS -> new TwelveDataParams("1week", 260);
            default -> {
                log.warn("Time frame {} is not supported by TwelveData, defaulting to 1 Day", timeFrame);
                yield new TwelveDataParams("5min", 78);
            }
        };
    }


}
