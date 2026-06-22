package jotalac.market_viewer.market_viewer_api.dto.auth;

public record JwtTokensDto(
        String token,
        String refreshToken
) {}
