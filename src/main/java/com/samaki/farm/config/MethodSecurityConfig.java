package com.samaki.farm.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

/**
 * Inawezesha @PreAuthorize("hasAuthority(...)")/@PreAuthorize("hasPermission(...)")
 * kwenye REST controllers (kama Lsms MethodSecurityConfig) - GraphQL resolvers
 * zinaendelea kutumia PermissionChecker (angalia darasa hilo) kwa sababu si
 * REST na zinahitaji muktadha maalum (farmId scoping) badala ya jibu la
 * boolean tu.
 */
@Configuration
@EnableMethodSecurity(prePostEnabled = true)
public class MethodSecurityConfig {

    @Autowired
    private PermissionEvaluator permissionEvaluator;

    @Bean
    public DefaultMethodSecurityExpressionHandler methodSecurityExpressionHandler() {
        DefaultMethodSecurityExpressionHandler expressionHandler = new DefaultMethodSecurityExpressionHandler();
        expressionHandler.setPermissionEvaluator(permissionEvaluator);
        return expressionHandler;
    }
}
