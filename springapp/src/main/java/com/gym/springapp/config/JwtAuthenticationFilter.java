package com.gym.springapp.config;

import com.gym.springapp.model.User;
import com.gym.springapp.repository.UserRepo;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Optional;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtils jwtUtils;
    private final UserRepo userRepo;

    public JwtAuthenticationFilter(JwtUtils jwtUtils, UserRepo userRepo) {
        this.jwtUtils = jwtUtils;
        this.userRepo = userRepo;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");
        String token = null;
        String email = null;

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
            try {
                email = jwtUtils.extractEmail(token);
            } catch (Exception e) {
                // Token parsing error, ignore and let filter chain handle unauthorized requests
            }
        }

        if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            Optional<User> userOpt = userRepo.findByEmailIgnoreCase(email);
            if (userOpt.isPresent()) {
                User user = userOpt.get();
                if (!user.isActive() || user.isDeleted()) {
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    String body = String.format(
                            "{\"timestamp\":\"%s\",\"status\":401,\"error\":\"Unauthorized\",\"message\":\"Account is deactivated or deleted\",\"path\":\"%s\"}",
                            LocalDateTime.now(),
                            request.getRequestURI());
                    response.getWriter().write(body);
                    return;
                }

                if (jwtUtils.validateToken(token, user.getEmail())) {
                    UserDetails userDetails = new UserPrinciple(user);
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(userDetails,
                            null, userDetails.getAuthorities());
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
        }

        filterChain.doFilter(request, response);
    }
}
