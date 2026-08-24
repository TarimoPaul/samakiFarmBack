package com.samaki.farm.auth.security;

import com.samaki.farm.common.exception.ErrorCodes;
import com.samaki.farm.common.exception.ForbiddenException;
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
     * Ruhusa inayogeuza usimamizi wa wanachama kuwa wa KAMPUNI NZIMA badala
     * ya shamba moja.
     *
     * Haikuongezwi hapa kama ruhusa mpya: ni ile ile inayomruhusu mtu
     * kuunda na kuorodhesha mashamba YOTE (angalia FarmController). Aliye
     * na mamlaka ya kupanga mashamba ya kampuni lazima aweze kupanga watu
     * ndani yake - vinginevyo angeunda shamba asiloweza kuliwekea mtu.
     */
    private static final String COMPANY_WIDE_PERMISSION = "manage_farms";

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

    /**
     * OPERESHENI ZA USIMAMIZI - farmId iliyoombwa lazima ilingane na shamba
     * la mtumiaji, ISIPOKUWA wenye mamlaka ya kampuni nzima.
     *
     * Inatumika pale farmId inapotoka kwa mteja kama sehemu ya ombi la
     * USIMAMIZI: kumweka mtu kwenye shamba, kubadilisha role yake,
     * kuorodhesha watu wa shamba (angalia FarmUserService/UserService).
     *
     * NGAZI MBILI, zinazotofautishwa na ruhusa PEKEE:
     *
     *  - 'manage_users' TU  -> shamba LAKE pekee. Huyu ni msimamizi wa
     *    shamba: anapanga watu wa shamba lake, na farmId nyingine yoyote
     *    ni 403.
     *  - 'manage_farms'     -> shamba LOLOTE. Huyu ni msimamizi wa kampuni.
     *    Ni sheria ile ile iliyokwisha kuwepo kwa GET /api/farms (anaona
     *    mashamba yote); hapa inapanuliwa kwa wanachama wake.
     *  - ROOT               -> shamba lolote, kama ilivyokuwa. (hasPermission
     *    yenyewe inampitisha ROOT, lakini isRoot() imeandikwa wazi hapa ili
     *    nia isitegemee undani wa AuthenticatedUser.) ROOT LAZIMA aweze
     *    kufanya hizi - ndiye anayeanzisha shamba jipya na kumweka mmiliki
     *    wake wa kwanza; bila bypass hii hakuna njia ya kuanzisha mfumo.
     *
     * Ukaguzi upo HAPA, si kwenye kila endpoint, kwa sababu ile ile ya
     * requireResourceInCallersFarm hapo chini: endpoint mpya ya wanachama
     * inaita method hii hii badala ya kunakili - au kusahau - sheria.
     *
     * KWA DATA YA UZALISHAJI tumia requireResourceInCallersFarm(...) badala
     * yake - ina sheria TOFAUTI kwa makusudi (angalia hapo chini). Ruhusa ya
     * kampuni HAIFUNGUI data ya uzalishaji ya shamba lingine; inafungua
     * usimamizi wa wanachama pekee (angalia FRONTEND_BACKEND_AUDIT.md, D-1).
     */
    public void requireSameFarm(Integer requestedFarmId) {
        AuthenticatedUser user = currentUser();
        if (user.isRoot() || user.hasPermission(COMPANY_WIDE_PERMISSION)) {
            return;
        }
        if (!requestedFarmId.equals(user.getFarmId())) {
            throw new AccessDeniedException("Huruhusiwi kufikia shamba hili.");
        }
    }

    /**
     * DATA YA UZALISHAJI - njia MOJA ya kuthibitisha kwamba rasilimali
     * iliyotajwa kwa kitambulisho (unit, cycle, n.k.) ni ya shamba la
     * mwombaji.
     *
     * Ipo kwa sababu ukaguzi huu ulikuwa umeandikwa ndani ya FeedService
     * pekee, na CycleService.create ilikuwa imeusahau kabisa: mtu mwenye
     * 'edit_cycle' kwenye shamba lolote aliweza kuunda mzunguko ndani ya
     * tanki la shamba LINGINE kwa kutuma unitId yake tu (angalia
     * FRONTEND_BACKEND_AUDIT.md, D-1). Ukaguzi ukiwa hapa, module mpya
     * inaita method hii hii badala ya kuandika - au kusahau - logic yake.
     *
     * SHERIA: anayefanya kazi kwenye data ya shamba LAZIMA awe na shamba.
     * HAKUNA bypass ya ROOT, tofauti na requireSameFarm hapo juu: ROOT ni
     * akaunti ya usimamizi (watumiaji/roles/mashamba) na hana farmId, hivyo
     * hana shamba la kulinganisha nalo. Kumruhusu kuandika data ya
     * uzalishaji kungemaanisha kuandika kwenye shamba lisilojulikana.
     */
    public void requireResourceInCallersFarm(Integer resourceFarmId) {
        Integer callersFarmId = requireFarmContext();
        if (!callersFarmId.equals(resourceFarmId)) {
            throw new AccessDeniedException("Huruhusiwi kufikia shamba hili.");
        }
    }

    /**
     * Ruhusa + shamba kwa hatua MOJA - mwanzo wa kila operesheni ya data
     * ya shamba.
     *
     * Inarudisha farmId badala ya AuthenticatedUser kwa makusudi: service
     * inayotaka shamba lake LAZIMA ipite hapa ili kulipata, hivyo haiwezi
     * kusahau ukaguzi na bado ikafanya kazi. (Kuisoma moja kwa moja kwa
     * user.getFarmId() ndiko kulikoruhusu null kupenya hadi kwenye
     * findById na kutokeza INTERNAL_ERROR.)
     */
    public Integer requireFarmScope(String permissionCode) {
        require(permissionCode);
        return requireFarmContext();
    }

    /**
     * farmId ya mwombaji, au ForbiddenException(NO_FARM_CONTEXT) kama
     * hana shamba.
     *
     * Ni ForbiddenException (si AccessDeniedException) kwa sababu inabeba
     * errorCode: frontend inapaswa kutofautisha "huna ruhusa" na "huna
     * shamba" - ya kwanza ni suala la role, ya pili ni suala la uanachama,
     * na zinatatuliwa kwa njia tofauti kabisa.
     */
    public Integer requireFarmContext() {
        AuthenticatedUser user = currentUser();
        if (user.getFarmId() == null) {
            throw new ForbiddenException(
                    user.isRoot()
                            ? "ROOT hana shamba; tumia akaunti ya shamba husika."
                            : "Bado hujapangiwa shamba lolote. Wasiliana na msimamizi.",
                    ErrorCodes.NO_FARM_CONTEXT);
        }
        return user.getFarmId();
    }
}
