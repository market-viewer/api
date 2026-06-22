package jotalac.market_viewer.market_viewer_api.exception.auth;

public class JwtException extends LoginException {
    public JwtException(String message) {
        super(message);
    }
}
