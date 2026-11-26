package com.gym.springapp.config;

import com.gym.springapp.model.Member;
import com.gym.springapp.model.User;
import com.gym.springapp.repository.MemberRepo;
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
    private final MemberRepo memberRepo;

    public JwtAuthenticationFilter(JwtUtils jwtUtils, UserRepo userRepo, MemberRepo memberRepo) {
        this.jwtUtils = jwtUtils;
        this.userRepo = userRepo;
        this.memberRepo = memberRepo;
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
                if (jwtUtils.validateToken(token, user.getEmail())) {
                    Optional<Member> memberOpt = memberRepo.findByUserId(user.getId());
                    boolean memberDeleted = memberOpt.map(Member::isDeleted).orElse(false);
                    if (user.isDeleted() || memberDeleted) {
                        writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized",
                                "Account is deleted", request.getRequestURI(), null);
                        return;
                    }

                    boolean memberDeactivated = memberOpt
                            .map(m -> "DEACTIVATED".equalsIgnoreCase(m.getStatus()) || !m.isActive())
                            .orElse(false);

                    if (!user.isActive() || memberDeactivated) {
                        writeError(response, HttpServletResponse.SC_FORBIDDEN, "Forbidden",
                                "Your account has been deactivated. Please contact the gym administrator.",
                                request.getRequestURI(), "MEMBER_DEACTIVATED");
                        return;
                    }

                    if (memberOpt.map(m -> "SUSPENDED".equalsIgnoreCase(m.getStatus())).orElse(false)) {
                        writeError(response, HttpServletResponse.SC_FORBIDDEN, "Forbidden",
                                "Your account has been suspended. Please contact the gym administrator.",
                                request.getRequestURI(), "MEMBER_SUSPENDED");
                        return;
                    }

                    Long tokenVersion = jwtUtils.extractTokenVersion(token);
                    long currentVersion = user.getEffectiveTokenVersion();
                    if ((tokenVersion == null && currentVersion > 0)
                            || (tokenVersion != null && tokenVersion.longValue() != currentVersion)) {
                        writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized",
                                "Session invalidated. Please log in again.", request.getRequestURI(), null);
                        return;
                    }

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

    private void writeError(HttpServletResponse response, int status, String error, String message,
                            String path, String code) throws IOException {
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setStatus(status);
        String codePart = code != null
                ? String.format("\"code\":\"%s\",\"error\":\"%s\",", code, code)
                : String.format("\"error\":\"%s\",", error);
        String body = String.format(
                "{\"timestamp\":\"%s\",\"status\":%d,%s\"message\":\"%s\",\"path\":\"%s\"}",
                LocalDateTime.now(),
                status,
                codePart,
                message.replace("\"", "\\\""),
                path);
        response.getWriter().write(body);
    }
}
