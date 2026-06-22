package jotalac.market_viewer.market_viewer_api.filter;

import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jotalac.market_viewer.market_viewer_api.dto.ErrorResponse;
import jotalac.market_viewer.market_viewer_api.entity.User;
import jotalac.market_viewer.market_viewer_api.repository.UserRepository;
import jotalac.market_viewer.market_viewer_api.service.auth.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain) throws ServletException, IOException {
        try {
            final String authHeader = request.getHeader("Authorization");
            String jwt = null;
            Integer userId = null;

            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                jwt = authHeader.substring(7);
                userId = jwtService.extractUserId(jwt);

                String tokenType = jwtService.extractTokenType(jwt);
                if (!"access".equals(tokenType)) {
                    sendResponse("Invalid token type", response, request.getRequestURI());
                    return;
                }
            }

            if (userId != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                User user = userRepository.findById(userId).orElseThrow(() -> new UsernameNotFoundException("User not found"));

                // check token version for revocation
                Integer tokenVersion = jwtService.extractTokenVersion(jwt);
                if (tokenVersion == null || !tokenVersion.equals(user.getTokenVersion())) {
                    sendResponse("Token has been revoked", response, request.getRequestURI());
                    return;
                }

                UserDetails userDetails = org.springframework.security.core.userdetails.User.builder()
                        .username(user.getUsername())
                        .password("")
                        .build();

                if (jwtService.validateToken(jwt, user.getId())) {
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            userDetails.getAuthorities()
                    );
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }

        } catch (ExpiredJwtException _) {
            sendResponse("Token expired", response, request.getRequestURI());
            return;
        } catch (UsernameNotFoundException _) {
            sendResponse("User not found", response, request.getRequestURI());
            return;
        } catch (Exception e) {
            log.info(e.getMessage());
            sendResponse("Invalid JWT token", response, request.getRequestURI());
            return;
        }
        filterChain.doFilter(request, response);
    }

    private void sendResponse(String message, HttpServletResponse response, String uri) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        ErrorResponse errorResponse = new ErrorResponse(LocalDateTime.now(), message, uri);
        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
    }

}
