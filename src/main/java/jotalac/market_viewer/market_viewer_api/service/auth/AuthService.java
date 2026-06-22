package jotalac.market_viewer.market_viewer_api.service.auth;


import io.jsonwebtoken.ExpiredJwtException;
import jotalac.market_viewer.market_viewer_api.dto.auth.*;
import jotalac.market_viewer.market_viewer_api.dto.user.UserDtoMapper;
import jotalac.market_viewer.market_viewer_api.entity.OAuthProvider;
import jotalac.market_viewer.market_viewer_api.entity.User;
import jotalac.market_viewer.market_viewer_api.exception.NotFoundException;
import jotalac.market_viewer.market_viewer_api.exception.auth.AccountRecoverException;
import jotalac.market_viewer.market_viewer_api.exception.auth.JwtException;
import jotalac.market_viewer.market_viewer_api.exception.auth.LoginException;
import jotalac.market_viewer.market_viewer_api.exception.auth.RegisterException;
import jotalac.market_viewer.market_viewer_api.exception.user.UserAlreadyExistsException;
import jotalac.market_viewer.market_viewer_api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RecoveryCodeService recoveryCodeService;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserDtoMapper userDtoMapper;


    @Transactional
    public RegisterResponseDto register(RegisterRequestDto registerRequestDto) {
        //check if passwords match
        if (!registerRequestDto.password().equals(registerRequestDto.passwordRepeat())) {
            throw new RegisterException("Passwords do not match");
        }

        //check if the user already exists
        checkUserExists(registerRequestDto.username());

        //save the user
        String hashedPassword = passwordEncoder.encode(registerRequestDto.password());
        User newUser = new User(registerRequestDto.username(), hashedPassword);
        userRepository.save(newUser);

        List<String> recoveryCodes = recoveryCodeService.generateAndSaveCodes(newUser);

        return new RegisterResponseDto("Registration successful", recoveryCodes);
    }

    @Transactional
    public JwtTokensDto login(LoginRequestDto request) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.username(), request.password())
            );
            if (!authentication.isAuthenticated()) {
                throw new LoginException("Invalid username or password");
            }

            User user = userRepository.findByUsername(request.username())
                    .orElseThrow(() -> new LoginException("Invalid username or password"));

            userRepository.increaseTokenVersion(user.getId());
            Integer tokenVersion = userRepository.findTokenVersionById(user.getId());

            String token = jwtService.generateToken(user.getId(), false, tokenVersion);
            String refreshToken = jwtService.generateToken(user.getId(), true, tokenVersion);
            return new JwtTokensDto(token, refreshToken);
        } catch(AuthenticationException e) {
            log.info(e.getMessage());
            throw new LoginException("Invalid username or password");
        }
    }

    @Transactional
    public JwtTokensDto refreshTokens(RefreshTokenDto refreshTokensDto) {
        try {
            boolean isTokenValid = jwtService.validateRefreshToken(refreshTokensDto.refreshToken());

            if (!isTokenValid) {
                throw new JwtException("Invalid refresh token");
            }

            Integer userId = jwtService.extractUserId(refreshTokensDto.refreshToken());

            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new JwtException("Invalid refresh token"));

            // verify token version hasn't been revoked
            Integer tokenVersionInToken = jwtService.extractTokenVersion(refreshTokensDto.refreshToken());
            if (tokenVersionInToken == null || !tokenVersionInToken.equals(user.getTokenVersion())) {
                throw new JwtException("Refresh token has been revoked");
            }

            userRepository.increaseTokenVersion(userId);
            Integer tokenVersion = userRepository.findTokenVersionById(userId);

            String token = jwtService.generateToken(userId, false, tokenVersion);
            String refreshToken = jwtService.generateToken(userId, true, tokenVersion);

            return new JwtTokensDto(token, refreshToken);
        } catch (ExpiredJwtException e) {
            throw new JwtException("Refresh token expired");
        }
    }

    @Transactional
    public void recoverAccount(RecoverRequestDto recoverDto) {
        //check if password match
        if (!recoverDto.newPassword().equals(recoverDto.newPasswordRepeat())) {
            throw new AccountRecoverException("Passwords do not match");
        }

        //check if the recovery code is valid
        User user = userRepository.findByUsername(recoverDto.username()).orElseThrow(() -> new NotFoundException("Invalid username or recovery code"));

        if (!recoveryCodeService.validateAndUseCode(user, recoverDto.recoverCode())) {
            throw new AccountRecoverException("Invalid username or recovery code");
        }

        //change the password
        String hashedPassword = passwordEncoder.encode(recoverDto.newPassword());
        user.setPassword(hashedPassword);

        //invalidate all existing tokens
        userRepository.increaseTokenVersion(user.getId());
    }

    private void checkUserExists(String username) {
        if (userRepository.existsByUsername(username)) {
            throw new UserAlreadyExistsException("User with username '" + username + "' already exists");
        }
    }

    // get user or register user if not already exists
    @Transactional
    public User findOrCreateOAuthUser(String providerUsername, OAuthProvider provider, String providerId) {
        Optional<User> existing = userRepository.findByOauthProviderAndOauthProviderId(provider, providerId);

        if (existing.isPresent()) {
            User user = existing.get();

            //synch name with github if necessary
            if (!providerUsername.equals(user.getUsername()) && !userRepository.existsByUsername(providerUsername)) {
                user.setUsername(providerUsername);
            }

            return user;
        }

        // create new oauth user
        String username = providerUsername;
        if (userRepository.existsByUsername(username)) {
            String randomSuffix = UUID.randomUUID().toString().substring(0, 4);
            username = username + "_" + randomSuffix;
        }

        User newUser = new User();
        newUser.setUsername(username);
        newUser.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
        newUser.setOauthProvider(provider);
        newUser.setOauthProviderId(providerId);

        return userRepository.save(newUser);
    }

}
