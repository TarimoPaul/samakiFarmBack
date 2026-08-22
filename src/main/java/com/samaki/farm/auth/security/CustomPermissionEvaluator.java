package com.samaki.farm.auth.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.Collection;

/**
 * Permission evaluator kwa @PreAuthorize("hasPermission(...)") kwenye REST
 * controllers (kama Lsms CustomPermissionEvaluator) - inaruhusu ukaguzi wa
 * ruhusa dhidi ya authorities zilizowekwa fresh na JwtAuthFilter.
 *
 * ROOT bypass (ROOT_USER/wildcard) hapa ni ya ziada tu kwa usalama: JwtAuthFilter
 * tayari inaongeza ruhusa ZOTE kwenye authorities za ROOT, hivyo hasAuthority()
 * ya kawaida pia ingefanya kazi - lakini bypass hii inasaidia kwa ruhusa
 * zinazokaguliwa kabla hazijaongezwa kwenye jedwali la permissions.
 */
@Component
public class CustomPermissionEvaluator implements PermissionEvaluator {

    private static final Logger logger = LoggerFactory.getLogger(CustomPermissionEvaluator.class);

    @Override
    public boolean hasPermission(Authentication authentication, Object targetDomainObject, Object permission) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();

        if (authorities.stream().anyMatch(a -> "ROOT_USER".equals(a.getAuthority()))) {
            logger.debug("ROOT bypass - ufikiaji kamili umetolewa");
            return true;
        }

        String requiredPermission = permission.toString();

        if (authorities.stream().anyMatch(a -> "*".equals(a.getAuthority()))) {
            return true;
        }

        if (authorities.stream().anyMatch(a -> requiredPermission.equals(a.getAuthority()))) {
            return true;
        }

        logger.debug("Ruhusa imekataliwa: {}", requiredPermission);
        return false;
    }

    @Override
    public boolean hasPermission(Authentication authentication, Serializable targetId,
                                  String targetType, Object permission) {
        return hasPermission(authentication, null, permission);
    }
}
