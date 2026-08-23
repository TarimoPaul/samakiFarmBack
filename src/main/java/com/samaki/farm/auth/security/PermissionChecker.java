package com.samaki.farm.auth.security;

import com.samaki.farm.common.exception.ErrorCodes;
import com.samaki.farm.common.exception.UnauthorizedException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Kikagua ruhusa cha pamoja - REST controllers na GraphQL resolvers zote
 * zinaita method hii hii (require()) badala ya kila moja kuandika logic
 * yake ya RBAC - hii ndiyo "chanzo kimoja cha ukweli" kwa RBAC kati ya
 * API mbili tofauti (REST + GraphQL).
 *
 * Bypass ya ufikiaji kamili ni flag ya isRoot (kama Lsms), SI jina la role
 * "OWNER" (kama ilivyokuwa awali) - OWNER sasa ni role ya kawaida inayopata
 * ruhusa zake kwa uwazi kupitia role_permissions (angalia RbacDataInitializer/
 * seed/role_permissions.csv), hivyo role hiyo inaweza kubadilishwa jina au
 * kufutwa bila kuvunja ufikiaji wa msimamizi mkuu wa mfumo.
 */
@Component
public class PermissionChecker {

    /**
     * Mtumiaji wa ombi la sasa.
     *
     * Inatupa UnauthorizedException (401, UNAUTHENTICATED) - SI
     * AccessDeniedException (403). Hii ni hali ya "hujajitambulisha kabisa",
     * si "tunakujua lakini huruhusiwi": kurudisha 403 kungemfanya mteja
     * aamini kikao chake bado ni halali wakati token yake imeisha muda.
     * Msimbo ni ule ule SecurityConfig.authenticationEntryPoint inaoutuma,
     * hivyo frontend inashughulikia hali hii mahali pamoja.
     *
     * getAuthentication() inaweza kuwa null (mfano filter haikuweka
     * authentication kwa sababu token ni mbovu). Bila ukaguzi huu ingekuwa
     * NullPointerException - yaani 500 badala ya 401.
     */
    public AuthenticatedUser currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Object principal = authentication == null ? null : authentication.getPrincipal();
        if (!(principal instanceof AuthenticatedUser authUser)) {
            throw new UnauthorizedException("Hujaingia (login) - hakuna token sahihi.",
                    ErrorCodes.UNAUTHENTICATED);
        }
        return authUser;
    }

    /** Inatupa AccessDeniedException kama mtumiaji hana ruhusa husika (isipokuwa ROOT). */
    public AuthenticatedUser require(String permissionCode) {
        AuthenticatedUser user = currentUser();
        if (!user.hasPermission(permissionCode)) {
            throw new AccessDeniedException("Huna ruhusa ya '" + permissionCode + "'.");
        }
        return user;
    }

    /** Inahakikisha farmId iliyoombwa inalingana na shamba la mtumiaji (isipokuwa ROOT). */
    public void requireSameFarm(Integer requestedFarmId) {
        AuthenticatedUser user = currentUser();
        if (!user.isRoot() && !requestedFarmId.equals(user.getFarmId())) {
            throw new AccessDeniedException("Huruhusiwi kufikia shamba hili.");
        }
    }
}
