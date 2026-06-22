package jotalac.market_viewer.market_viewer_api.dto.auth;


import jakarta.validation.constraints.NotEmpty;

public record RefreshTokenDto(
        @NotEmpty
        String refreshToken
) {}
