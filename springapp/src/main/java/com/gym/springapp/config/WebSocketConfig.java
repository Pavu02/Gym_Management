package com.gym.springapp.config;

import com.gym.springapp.model.User;
import com.gym.springapp.repository.UserRepo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.security.Principal;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtUtils jwtUtils;
    private final UserRepo userRepo;

    @Value("${app.cors.allowed-origins:http://localhost:4200,http://localhost:8081}")
    private String allowedOriginsProp;

    @Value("${app.frontend-url:http://localhost:4200}")
    private String frontendUrl;

    public WebSocketConfig(JwtUtils jwtUtils, UserRepo userRepo) {
        this.jwtUtils = jwtUtils;
        this.userRepo = userRepo;
    }

    private String[] resolveAllowedOrigins() {
        Set<String> origins = new LinkedHashSet<>();
        addOrigins(origins, allowedOriginsProp);
        addOrigins(origins, frontendUrl);
        if (origins.isEmpty()) {
            origins.add("http://localhost:4200");
            origins.add("http://localhost:8081");
        }
        return origins.toArray(String[]::new);
    }

    private void addOrigins(Set<String> target, String csv) {
        if (csv == null || csv.isBlank()) {
            return;
        }
        Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty() && !"*".equals(s))
                .forEach(target::add);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic");
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        String[] origins = resolveAllowedOrigins();
        registry.addEndpoint("/ws-checkin")
                .setAllowedOrigins(origins);
        registry.addEndpoint("/ws-checkin-sockjs")
                .setAllowedOrigins(origins)
                .withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor == null) {
                    return message;
                }

                if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                    String authHeader = accessor.getFirstNativeHeader("Authorization");
                    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                        throw new AccessDeniedException("Missing STOMP Authorization header");
                    }
                    String token = authHeader.substring(7);
                    String email = jwtUtils.extractEmail(token);
                    User user = userRepo.findByEmailIgnoreCase(email)
                            .orElseThrow(() -> new AccessDeniedException("Unknown WebSocket user"));
                    if (!user.isActive() || user.isDeleted() || !jwtUtils.validateToken(token, user.getEmail())) {
                        throw new AccessDeniedException("Invalid or inactive WebSocket credentials");
                    }
                    UserPrinciple principle = new UserPrinciple(user);
                    UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                            principle, null, principle.getAuthorities());
                    accessor.setUser(auth);
                } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
                    String dest = accessor.getDestination();
                    Principal userPrincipal = accessor.getUser();
                    if (userPrincipal instanceof UsernamePasswordAuthenticationToken authToken) {
                        boolean isAdmin = authToken.getAuthorities().stream()
                                .map(GrantedAuthority::getAuthority)
                                .anyMatch("ROLE_ADMIN"::equals);
                        if (dest != null && (dest.startsWith("/topic/checkin-sessions/") || dest.startsWith("/topic/admin/")) && !isAdmin) {
                            throw new AccessDeniedException("Only ADMIN may subscribe to check-in session feeds");
                        }
                    } else if (dest != null && dest.startsWith("/topic/")) {
                        throw new AccessDeniedException("Unauthenticated STOMP subscription rejected");
                    }
                }
                return message;
            }
        });
    }
}
