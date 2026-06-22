package jotalac.market_viewer.market_viewer_api.service.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Service
public class JwtService {

    @Value("${JWT_SECRET}")
    private String SECRET;

    @Value("${JWT_EXPIRATION}")
    private long TOKEN_EXPIRATION;

    @Value("${JWT_REFRESH_EXPIRATION}")
    private long TOKEN_REFRESH_EXPIRATION;

    private static final String ISSUER = "market-viewer";

    public String generateToken(Integer userId, boolean isRefresh, Integer tokenVersion) {
        //claims are the data inside the token
        Map<String, Object> claims = new HashMap<>();
        claims.put("tokenVersion", tokenVersion);

        return createToken(claims, userId, isRefresh);
    }

    private String createToken(Map<String, Object> claims, Integer userId, boolean isRefresh) {
        long tokenExpiration = isRefresh ? TOKEN_REFRESH_EXPIRATION : TOKEN_EXPIRATION;
        claims.put("type", isRefresh ? "refresh" : "access");

        return Jwts.builder()
                .claims(claims)
                .subject(userId.toString())
                .issuer(ISSUER)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + tokenExpiration))
                .signWith(getSignKey())
                .compact();
    }

    // need to decode it to have the full length secret for the hashing algorithm
    private SecretKey getSignKey() {
        byte[] keyBytes = Decoders.BASE64.decode(SECRET);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public Integer extractUserId(String token) {
        return Integer.valueOf(extractClaim(token, Claims::getSubject));
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public String extractTokenType(String token) {
        return extractClaim(token, claims -> claims.get("type", String.class));
    }

    public Integer extractTokenVersion(String token) {
        return extractClaim(token, claims -> claims.get("tokenVersion", Integer.class));
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSignKey())
                .requireIssuer(ISSUER)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public Boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    public boolean validateToken(String token, Integer userId) {
        final Integer tokenUserId = extractUserId(token);
        return (userId.equals(tokenUserId) && !isTokenExpired(token));
    }

    public boolean validateRefreshToken(String token) {
        return extractTokenType(token).equals("refresh") && !isTokenExpired(token);
    }
}
