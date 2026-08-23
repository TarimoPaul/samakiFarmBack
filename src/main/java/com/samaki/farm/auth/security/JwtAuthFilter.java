package com.samaki.farm.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.samaki.farm.common.exception.ErrorCodes;
import com.samaki.farm.common.web.ApiResponse;
import com.samaki.farm.farmuser.entity.FarmUser;
import com.samaki.farm.farmuser.repository.FarmUserRepository;
import com.samaki.farm.rbac.entity.Permission;
import com.samaki.farm.rbac.entity.Role;
import com.samaki.farm.user.entity.User;
import com.samaki.farm.user.entity.UserStatus;
import com.samaki.farm.user.repository.UserRepository;
import com.samaki.farm.rbac.repository.PermissionRepository;
import com.samaki.farm.rbac.repository.RoleRepository;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Filter moja inayotumika kwa REST NA GraphQL (zote mbili zinapita kwenye
 * Servlet filter chain ya Spring kabla ya kufika controller/resolver).
 * Ikithibitisha token, inaweka AuthenticatedUser kama principal - REST
 * controllers na GraphQL @SchemaMapping methods zote zinaweza kuipata kwa
 * njia ile ile (angalia CurrentUserArgumentResolver kwa GraphQL).
 *
 * RBAC "fresh kutoka DB" (kama Lsms AuthTokenFilter): JWT inabeba tu userId +
 * isRoot - SI ruhusa. Kila request, role/ruhusa za mtumiaji zinasomwa upya
 * kutoka DB (kwa cache fupi kupunguza msongamano wa DB) badala ya kuamini
 * orodha ya ruhusa iliyo-bake kwenye token wakati wa login - hivyo
 * mabadiliko ya role/ruhusa yanaanza kufanya kazi papo hapo bila mtumiaji
 * kulazimika ku-login tena.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(JwtAuthFilter.class);
    private static final String ROLE_PREFIX = "ROLE_";

    // Cache ya authorities za ROOT (ROOT ana ruhusa ZOTE zilizopo DB-ni)
    private static volatile Set<GrantedAuthority> cachedRootAuthorities = null;
    private static volatile long rootCacheTimestamp = 0;
    private static final long ROOT_CACHE_TTL_MS = 5 * 60 * 1000; // dakika 5

    /**
     * Hali ya akaunti inasafiri pamoja na principal kwa sababu vizuizi vya
     * B8 (DISABLED / PENDING / must_change_password) vinahitaji kujua
     * SABABU halisi ili kurudisha errorCode sahihi - si "hana ruhusa" tu.
     *
     * `status == null` inamaanisha mtu hayupo au amefutwa (soft-delete).
     */
    private record Account(AuthenticatedUser principal, UserStatus status, boolean mustChangePassword) {}

    // Cache ya kila mtumiaji - inaepusha kusoma DB kwenye kila request
    private record CachedAccount(Account account, long timestamp) {}
    private static final ConcurrentHashMap<UUID, CachedAccount> userCache = new ConcurrentHashMap<>();
    private static final long USER_CACHE_TTL_MS = 15 * 60 * 1000; // dakika 15

    /**
     * Endpoints za auth zimeachwa nje ya kizuizi cha must_change_password:
     * /change-password ndiyo njia ya kutoka, na nyingine (login, register,
     * forgot/reset-password) hazionyeshi data yoyote ya shamba. Bila
     * ruhusa hii mtu angekwama bila njia ya kujinasua.
     */
    private static final String AUTH_PATH_PREFIX = "/api/auth/";

    /** Futa cache ya ROOT - ita hii pale permissions/roles zinapobadilika. */
    public static void clearRootCache() {
        synchronized (JwtAuthFilter.class) {
            cachedRootAuthorities = null;
            rootCacheTimestamp = 0;
        }
        logger.info("ROOT authority cache imefutwa");
    }

    /** Futa cache ya mtumiaji mmoja - ita hii pale role/farm yake inapobadilika. */
    public static void clearUserCache(UUID userId) {
        userCache.remove(userId);
    }

    /** Futa cache ya watumiaji wote - ita hii pale role inapobadilisha ruhusa zake. */
    public static void clearAllUserCache() {
        userCache.clear();
        logger.info("Authority cache za watumiaji wote zimefutwa");
    }

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private final FarmUserRepository farmUserRepository;
    private final PermissionRepository permissionRepository;
    private final RoleRepository roleRepository;
    private final ObjectMapper objectMapper;

    public JwtAuthFilter(JwtUtil jwtUtil, UserRepository userRepository,
                          FarmUserRepository farmUserRepository,
                          PermissionRepository permissionRepository, RoleRepository roleRepository,
                          ObjectMapper objectMapper) {
        this.jwtUtil = jwtUtil;
        this.userRepository = userRepository;
        this.farmUserRepository = farmUserRepository;
        this.permissionRepository = permissionRepository;
        this.roleRepository = roleRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        Account account;
        Set<GrantedAuthority> authorities;
        try {
            Claims claims = jwtUtil.parseToken(header.substring(7));
            UUID userId = UUID.fromString(claims.getSubject());

            account = resolveAccount(userId);
            // isRoot inatoka DB (si claim ya token): mtu aliyeondolewa uroot
            // anapoteza ufikiaji papo hapo badala ya kusubiri token iishe.
            authorities = account.principal().isRoot()
                    ? getRootAuthorities() : getUserAuthorities(account.principal());
        } catch (Exception e) {
            // Token si sahihi/imeisha - endelea bila kuweka authentication;
            // endpoints zinazohitaji auth zitakataa ombi kwenye SecurityConfig/resolver checks.
            // Logi (debug) ni muhimu - bila hii, makosa mengine (mfano
            // LazyInitializationException wakati wa kusoma DB) yangepotea
            // kimya na kuonekana kama "token si sahihi" tu.
            logger.debug("JWT auth imeshindikana: {}", e.toString());
            SecurityContextHolder.clearContext();
            filterChain.doFilter(request, response);
            return;
        }

        // ---------- Vizuizi vya akaunti (B8) ----------
        // Viko HAPA, si kwenye @PreAuthorize, kwa sababu vinapaswa kufunga
        // REST NA GraphQL kwa pamoja, na kwa sababu jibu linahitaji
        // errorCode maalum ambayo AccessDeniedException haina.

        // Mtu hayupo au amefutwa - jibu ni la mtu asiye na token kabisa (401),
        // si maelezo yanayothibitisha kuwa akaunti iliwahi kuwepo.
        if (account.status() == null) {
            SecurityContextHolder.clearContext();
            filterChain.doFilter(request, response);
            return;
        }

        if (account.status() == UserStatus.DISABLED) {
            deny(response, "Akaunti yako imezuiwa. Wasiliana na msimamizi.",
                    ErrorCodes.ACCOUNT_DISABLED);
            return;
        }

        if (account.status() == UserStatus.PENDING_APPROVAL) {
            deny(response, "Akaunti yako bado haijaidhinishwa. Subiri msimamizi akuruhusu.",
                    ErrorCodes.PENDING_APPROVAL);
            return;
        }

        if (account.mustChangePassword() && !isAuthPath(request)) {
            deny(response, "Lazima ubadilishe password kabla ya kuendelea kutumia mfumo.",
                    ErrorCodes.MUST_CHANGE_PASSWORD);
            return;
        }

        var authentication = new UsernamePasswordAuthenticationToken(account.principal(), null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);
        filterChain.doFilter(request, response);
    }

    /** Ombi liko kwenye /api/auth/** - halizuiwi na must_change_password. */
    private boolean isAuthPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri != null && uri.startsWith(AUTH_PATH_PREFIX);
    }

    /**
     * Inakataa ombi hapa hapa kwenye filter chain, kabla halijafika
     * DispatcherServlet - hivyo GlobalExceptionHandler haiihusiki na
     * envelope lazima iandikwe kwa mkono (kama SecurityConfig inavyofanya
     * kwa 401/403 za filter-chain level).
     */
    private void deny(HttpServletResponse response, String message, String errorCode) throws IOException {
        SecurityContextHolder.clearContext();
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), ApiResponse.error(message, errorCode));
    }

    private AuthenticatedUser resolveRoot(UUID userId) {
        // ROOT hana farm/role - ufikiaji wake unatoka kwenye flag, si uhusiano wowote
        return new AuthenticatedUser(userId, null, null, "ROOT", List.of(), true);
    }

    private Account resolveAccount(UUID userId) {
        long now = System.currentTimeMillis();
        CachedAccount cached = userCache.get(userId);
        if (cached != null && (now - cached.timestamp()) < USER_CACHE_TTL_MS) {
            return cached.account();
        }

        Account fresh = loadAccountFromDatabase(userId);
        userCache.put(userId, new CachedAccount(fresh, now));
        return fresh;
    }

    /**
     * Hatua mbili: mtu kwanza (hali ya akaunti), kisha uanachama wake.
     *
     * Hali inakaguliwa HAPA, si wakati wa login pekee: mtu aliyezuiwa
     * (DISABLED) au kufutwa anapoteza ruhusa ZOTE mara moja, hata kama
     * token yake bado haijaisha muda. UserService inafuta cache yake
     * anapobadilishwa, hivyo athari ni ya papo hapo.
     *
     * ROOT naye anasomwa DB hapa (tofauti na awali alipokuwa haguswi
     * kabisa): bila hivyo `must_change_password` na `status` zake
     * zisingeweza kulazimishwa - na ndiye hasa mwenye flag hiyo.
     */
    private Account loadAccountFromDatabase(UUID userId) {
        AuthenticatedUser noAccess = new AuthenticatedUser(userId, null, null, null, List.of(), false);

        // findByUserId ni derived query, hivyo @SQLRestriction inatumika:
        // mtu aliyefutwa harudishwi kabisa.
        User user = userRepository.findByUserId(userId).orElse(null);
        if (user == null) {
            return new Account(noAccess, null, false);
        }

        UserStatus status = user.getStatus();
        boolean mustChange = user.isMustChangePassword();

        if (Boolean.TRUE.equals(user.getIsRoot())) {
            return new Account(resolveRoot(userId), status, mustChange);
        }

        if (status != UserStatus.ACTIVE) {
            return new Account(noAccess, status, mustChange);
        }

        // TODO: farm switching - kwa sasa uanachama wa kwanza pekee
        // (umepangwa kwa farmId ili uwe thabiti).
        List<FarmUser> memberships = farmUserRepository.findByUser_UserIdOrderByFarm_FarmIdAsc(userId);
        if (memberships.isEmpty()) {
            // Mtu ameidhinishwa lakini hajapangiwa shamba - anaingia, lakini
            // hana ruhusa yoyote. Hii ni hali HALALI (Part A #4).
            return new Account(noAccess, status, mustChange);
        }

        FarmUser membership = memberships.get(0);
        Role role = membership.getRole();
        List<String> permissionCodes = (role == null || role.getPermissions() == null) ? List.of()
                : role.getPermissions().stream().map(Permission::getCode).toList();

        AuthenticatedUser principal = new AuthenticatedUser(
                userId,
                membership.getFarm() == null ? null : membership.getFarm().getFarmId(),
                role == null ? null : role.getRoleId(),
                role == null ? null : role.getName(),
                permissionCodes,
                false);

        return new Account(principal, status, mustChange);
    }

    /**
     * ROOT anapata ruhusa ZOTE zilizopo DB-ni + majina ya roles zote (ROLE_ prefix)
     * + wildcard "*" + "ROOT_USER" - sawa na Lsms getRootAuthorities().
     */
    private Set<GrantedAuthority> getRootAuthorities() {
        long now = System.currentTimeMillis();

        if (cachedRootAuthorities == null || (now - rootCacheTimestamp) > ROOT_CACHE_TTL_MS) {
            synchronized (JwtAuthFilter.class) {
                if (cachedRootAuthorities == null || (now - rootCacheTimestamp) > ROOT_CACHE_TTL_MS) {
                    Set<GrantedAuthority> authorities = new HashSet<>();

                    permissionRepository.findAll().forEach(permission ->
                            authorities.add(new SimpleGrantedAuthority(permission.getCode())));

                    roleRepository.findAll().forEach(role ->
                            authorities.add(new SimpleGrantedAuthority(ROLE_PREFIX + role.getName().toUpperCase())));

                    authorities.add(new SimpleGrantedAuthority("*"));
                    authorities.add(new SimpleGrantedAuthority("ROOT_USER"));

                    logger.info("ROOT authorities cache imesasishwa na authorities {}", authorities.size());
                    cachedRootAuthorities = Collections.unmodifiableSet(authorities);
                    rootCacheTimestamp = now;
                }
            }
        }
        return new HashSet<>(cachedRootAuthorities);
    }

    private Set<GrantedAuthority> getUserAuthorities(AuthenticatedUser user) {
        Set<GrantedAuthority> authorities = new HashSet<>();
        if (user.getRoleName() != null) {
            authorities.add(new SimpleGrantedAuthority(ROLE_PREFIX + user.getRoleName().toUpperCase()));
        }
        if (user.getPermissions() != null) {
            user.getPermissions().forEach(code -> authorities.add(new SimpleGrantedAuthority(code)));
        }
        return authorities;
    }
}
