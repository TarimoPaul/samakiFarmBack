package com.samaki.farm.config;

import com.samaki.farm.auth.security.AuthenticatedUser;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.UUID;

/**
 * Inawezesha @CreatedDate/@LastModifiedDate/@LastModifiedBy za BaseEntity.
 *
 * AuditorAware ndiyo inayojibu swali "ni nani anayefanya mabadiliko haya?" -
 * inasoma AuthenticatedUser aliyewekwa na JwtAuthFilter kwenye
 * SecurityContext. Ikirudisha Optional.empty() (mfano wakati wa seeding ya
 * kuanza kwa app, au kazi ya background isiyo na mtumiaji), updatedBy
 * inabaki null badala ya kuvunja operesheni.
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
public class AuditingConfig {

    @Bean
    public AuditorAware<UUID> auditorAware() {
        return () -> {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated()) {
                return Optional.empty();
            }
            if (authentication.getPrincipal() instanceof AuthenticatedUser user) {
                return Optional.ofNullable(user.getUserId());
            }
            return Optional.empty();
        };
    }
}
