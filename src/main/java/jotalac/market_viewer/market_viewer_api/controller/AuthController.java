package jotalac.market_viewer.market_viewer_api.controller;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jotalac.market_viewer.market_viewer_api.dto.MessageResponse;
import jotalac.market_viewer.market_viewer_api.dto.auth.*;
import jotalac.market_viewer.market_viewer_api.dto.user.UserDtoMapper;
import jotalac.market_viewer.market_viewer_api.service.UserService;
import jotalac.market_viewer.market_viewer_api.service.auth.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final UserDtoMapper userDtoMapper;
    private final AuthService authService;


    @PostMapping("/register")
    public ResponseEntity<RegisterResponseDto> registerUser(@Valid @RequestBody RegisterRequestDto registerRequestDto) {
        RegisterResponseDto registerResponse = authService.register(registerRequestDto);

        return  ResponseEntity.status(HttpStatus.CREATED).body(registerResponse);
    }

    @PostMapping("/login")
    public ResponseEntity<JwtTokensDto> login(@Valid @RequestBody LoginRequestDto loginRequestDto) {
        JwtTokensDto loginResponse = authService.login(loginRequestDto);

        return  ResponseEntity.status(HttpStatus.OK).body(loginResponse);
    }

    @PostMapping("/recover")
    public ResponseEntity<MessageResponse> recover(@Valid @RequestBody RecoverRequestDto recoverRequestDto) {
        authService.recoverAccount(recoverRequestDto);

        return ResponseEntity.status(HttpStatus.OK).body(new MessageResponse("Password updated successfully"));
    }

    @PostMapping("/refresh")
    public ResponseEntity<JwtTokensDto> refreshToken(@Valid @RequestBody RefreshTokenDto refreshTokenDto) {
        JwtTokensDto newTokens = authService.refreshTokens(refreshTokenDto);

        return ResponseEntity.status(HttpStatus.OK).body(newTokens);
    }

    //sso endpoint for mobile devices - redirect to different uri
    @GetMapping("/sso/mobile")
    public void startMobileSSOAuth(HttpServletResponse response) throws IOException {
        // add the cookie to the response (that user is on mobile)
        Cookie mobileCookie = new Cookie("client_type", "mobile");
        mobileCookie.setPath("/");
        mobileCookie.setHttpOnly(true);
        mobileCookie.setMaxAge(300);

        response.addCookie(mobileCookie);

        //redirect to the default sso entrypoint
        response.sendRedirect("/oauth2/authorization/github");
    }

}
