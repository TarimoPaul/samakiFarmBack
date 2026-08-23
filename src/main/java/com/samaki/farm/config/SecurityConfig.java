package com.samaki.farm.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.samaki.farm.auth.security.JwtAuthFilter;
import com.samaki.farm.common.exception.ErrorCodes;
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
                // LAZIMA iwe KABLA ya /api/auth/** hapa chini - matcher ya
                // kwanza inayolingana ndiyo inayotumika. Tofauti na auth
                // endpoints nyingine, kubadilisha password kunahitaji token
                // halali (uthibitisho ni token + password ya sasa, si OTP).
                .requestMatchers("/api/auth/change-password").authenticated()
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/actuator/health").permitAll()
                // /graphql inaingia hapa chini kwenye anyRequest() - yaani
                // INAHITAJI token halali kwenye filter chain, kama endpoint
                // nyingine yoyote. (Maoni ya awali hapa yalidai ni "wazi";
                // hayakuwa yakieleza sheria halisi.)
                //
                // Tabaka MBILI, kwa makusudi:
                //  1. Hapa - "umeingia?" Ombi lisilo na token linakatwa
                //     kabla halijafika injini ya GraphQL, likipata 401
                //     UNAUTHENTICATED kutoka authenticationEntryPoint hapa
                //     chini. JwtAuthFilter nayo inakata DISABLED /
                //     PENDING_APPROVAL / must_change_password mapema zaidi.
                //  2. Ndani ya kila resolver - "unaruhusiwa KUFANYA HILI?"
                //     kupitia PermissionChecker.require(...) (angalia
                //     CycleResolver/ProductionUnitResolver/FeedResolver).
                //     Ukaguzi huu HAUWEZI kufanyika hapa: operesheni zote za
                //     GraphQL zinapita kwenye URL moja (/graphql) lakini kila
                //     moja inahitaji ruhusa yake.
                //
                // Tabaka la kwanza si la ziada lisilo na maana - ni
                // defence-in-depth: resolver mpya ikisahau kuita
                // PermissionChecker, bado haifikiwi na mtu asiye na token.
                //
                // REST controllers zinatumia @PreAuthorize (angalia
                // MethodSecurityConfig) kwa tabaka la pili.
                .anyRequest().authenticated()
            )
            // Makosa ya 401/403 yanayotokea kwenye filter chain yenyewe (kabla
            // ya kufika DispatcherServlet - mfano token haipo kabisa) hayapiti
            // kwenye GlobalExceptionHandler, hivyo yanahitaji kuandikwa kwa
            // ApiResponse envelope hapa hapa ili mteja apate muundo ule ule
            // wa jibu kila wakati (kama Lsms) badala ya ukurasa wa default
            // wa Spring Security.
            .exceptionHandling(ex -> ex
                // Msimbo ule ule PermissionChecker.currentUser() inaoutupa:
                // "hakuna kikao halali" ina maana moja kwa frontend, ikikatwa
                // hapa (filter chain, kabla ya DispatcherServlet) au ndani ya
                // controller/resolver. Awali envelope hii ilikuwa haina
                // errorCode kabisa, hivyo mteja alilazimika kutawi kwa ujumbe
                // wa Kiswahili.
                .authenticationEntryPoint((request, response, authException) ->
                        writeJsonError(response, 401, "Hujaingia (login) - token haipo au si sahihi.",
                                ErrorCodes.UNAUTHENTICATED))
                // Msimbo ule ule wa GlobalExceptionHandler.handleAccessDenied:
                // permission-denied ina maana moja kwa frontend haijalishi
                // imekataliwa hapa (filter chain) au ndani ya controller.
                .accessDeniedHandler((request, response, accessDeniedException) ->
                        writeJsonError(response, 403, "Huna ruhusa ya kufikia rasilimali hii.",
                                ErrorCodes.FORBIDDEN))
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    private void writeJsonError(jakarta.servlet.http.HttpServletResponse response, int status,
                                 String message, String errorCode) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(),
                errorCode == null ? ApiResponse.error(message) : ApiResponse.error(message, errorCode));
    }
}
