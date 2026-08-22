package com.samaki.farm.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.samaki.farm.auth.security.JwtAuthFilter;
import com.samaki.farm.common.web.ApiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final ObjectMapper objectMapper;

    // Angular dev server (ng serve) inaishi localhost:4200 kwa default - CORS
    // inahitajika kwa sababu frontend (4200) na backend (8080) ni origins
    // tofauti. Inaweza kubadilishwa kwa env var wakati wa deploy ya production
    // (mfano https://app.samakifarm.co.tz) bila kubadilisha code.
    @Value("${app.cors.allowed-origins:http://localhost:4200}")
    private String allowedOrigins;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter, ObjectMapper objectMapper) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.objectMapper = objectMapper;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(allowedOrigins.split(",")));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable()) // API pekee (JWT), si session/cookie based
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/actuator/health").permitAll()
                // /graphql yenyewe ni "wazi" hapa - RBAC halisi inafanyika ndani ya
                // resolvers kupitia PermissionChecker.require(...) (angalia
                // CycleResolver/ProductionUnitResolver), si kwenye Security layer,
                // kwa sababu operesheni tofauti za GraphQL kwenye endpoint moja
                // zinahitaji ruhusa tofauti-tofauti. REST controllers zinatumia
                // @PreAuthorize (angalia MethodSecurityConfig) badala yake.
                .anyRequest().authenticated()
            )
            // Makosa ya 401/403 yanayotokea kwenye filter chain yenyewe (kabla
            // ya kufika DispatcherServlet - mfano token haipo kabisa) hayapiti
            // kwenye GlobalExceptionHandler, hivyo yanahitaji kuandikwa kwa
            // ApiResponse envelope hapa hapa ili mteja apate muundo ule ule
            // wa jibu kila wakati (kama Lsms) badala ya ukurasa wa default
            // wa Spring Security.
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) ->
                        writeJsonError(response, 401, "Hujaingia (login) - token haipo au si sahihi."))
                .accessDeniedHandler((request, response, accessDeniedException) ->
                        writeJsonError(response, 403, "Huna ruhusa ya kufikia rasilimali hii."))
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    private void writeJsonError(jakarta.servlet.http.HttpServletResponse response, int status, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), ApiResponse.error(message));
    }
}
