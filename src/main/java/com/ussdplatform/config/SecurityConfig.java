package com.ussdplatform.config;

import com.ussdplatform.repository.UserRepository;
import com.ussdplatform.security.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
@Slf4j
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        log.info("SecurityFilterChain configured");
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/ussd/webhook/**").permitAll()
                .requestMatchers("/api/auth/register", "/api/auth/login", "/api/auth/verify", "/api/auth/verify-otp", "/api/auth/resend-verification").permitAll()
                .requestMatchers("/api/admin/login", "/api/admin/setup").permitAll()
                .requestMatchers("/api/team/invite/validate", "/api/team/invite/accept").permitAll()
                .requestMatchers("/api/billing/paystack/webhook").permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Component
    @RequiredArgsConstructor
    @Slf4j
    static class JwtAuthFilter extends OncePerRequestFilter {
        private final JwtService jwtService;
        private final UserRepository userRepository;

        @Override
        protected void doFilterInternal(HttpServletRequest request,
                                        HttpServletResponse response,
                                        FilterChain chain) throws ServletException, IOException {
            String uri = request.getRequestURI();
            String header = request.getHeader("Authorization");

            if (header != null && header.startsWith("Bearer ")) {
                String token = header.substring(7);
                log.debug("JWT filter: token found for {}", uri);
                if (jwtService.isValid(token)) {
                    String email = jwtService.extractEmail(token);
                    log.debug("JWT filter: valid token for email={}", email);
                    userRepository.findByEmail(email).ifPresentOrElse(
                        user -> {
                            var auth = new UsernamePasswordAuthenticationToken(user, null, List.of());
                            SecurityContextHolder.getContext().setAuthentication(auth);
                            log.debug("JWT filter: authenticated user={}", email);
                        },
                        () -> log.warn("JWT filter: token valid but user not found email={}", email)
                    );
                } else {
                    log.warn("JWT filter: invalid/expired token for {}", uri);
                }
            } else {
                log.debug("JWT filter: no token for {} (public endpoint or missing header)", uri);
            }

            chain.doFilter(request, response);
        }
    }
}
