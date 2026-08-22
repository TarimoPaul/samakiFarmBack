package com.samaki.farm.auth.security;

import com.samaki.farm.farmuser.entity.FarmUser;
import com.samaki.farm.rbac.entity.Permission;
import com.samaki.farm.rbac.entity.Role;
import com.samaki.farm.farmuser.repository.FarmUserRepository;
import com.samaki.farm.rbac.repository.PermissionRepository;
import com.samaki.farm.rbac.repository.RoleRepository;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    // Cache ya kila mtumiaji wa kawaida - inaepusha kusoma DB kwenye kila request
    private record CachedUser(AuthenticatedUser user, long timestamp) {}
    private static final ConcurrentHashMap<UUID, CachedUser> userCache = new ConcurrentHashMap<>();
    private static final long USER_CACHE_TTL_MS = 15 * 60 * 1000; // dakika 15

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
    private final FarmUserRepository farmUserRepository;
    private final PermissionRepository permissionRepository;
    private final RoleRepository roleRepository;

    public JwtAuthFilter(JwtUtil jwtUtil, FarmUserRepository farmUserRepository,
                          PermissionRepository permissionRepository, RoleRepository roleRepository) {
        this.jwtUtil = jwtUtil;
        this.farmUserRepository = farmUserRepository;
        this.permissionRepository = permissionRepository;
        this.roleRepository = roleRepository;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            try {
                Claims claims = jwtUtil.parseToken(token);
                UUID userId = UUID.fromString(claims.getSubject());
                boolean isRoot = Boolean.TRUE.equals(claims.get("isRoot", Boolean.class));

                AuthenticatedUser authUser = isRoot ? resolveRoot(userId) : resolveRegularUser(userId);
                Set<GrantedAuthority> authorities = isRoot ? getRootAuthorities() : getUserAuthorities(authUser);

                var authentication = new UsernamePasswordAuthenticationToken(authUser, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (Exception e) {
                // Token si sahihi/imeisha - endelea bila kuweka authentication;
                // endpoints zinazohitaji auth zitakataa ombi kwenye SecurityConfig/resolver checks.
                // Logi (debug) ni muhimu - bila hii, makosa mengine (mfano
                // LazyInitializationException wakati wa kusoma DB) yangepotea
                // kimya na kuonekana kama "token si sahihi" tu.
                logger.debug("JWT auth imeshindikana: {}", e.toString());
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }

    private AuthenticatedUser resolveRoot(UUID userId) {
        // ROOT hana farm/role - ufikiaji wake unatoka kwenye flag, si uhusiano wowote
        return new AuthenticatedUser(userId, null, null, "ROOT", List.of(), true);
    }

    private AuthenticatedUser resolveRegularUser(UUID userId) {
        long now = System.currentTimeMillis();
        CachedUser cached = userCache.get(userId);
        if (cached != null && (now - cached.timestamp()) < USER_CACHE_TTL_MS) {
            return cached.user();
        }

        // Baada ya kuunganisha User+FarmUser: query MOJA badala ya "tafuta
        // mtumiaji, kisha tafuta uanachama wake". farm/role zinaweza kuwa null
        // (mtumiaji hajaunganishwa na shamba bado) - hivyo kila mojawapo
        // inakaguliwa peke yake badala ya kuchukulia zote zipo.
        FarmUser farmUser = farmUserRepository.findByUserId(userId).orElse(null);
        AuthenticatedUser fresh;
        if (farmUser == null) {
            fresh = new AuthenticatedUser(userId, null, null, null, List.of(), false);
        } else {
            Role role = farmUser.getRole();
            List<String> permissionCodes = (role == null || role.getPermissions() == null) ? List.of() :
                    role.getPermissions().stream().map(Permission::getCode).toList();
            fresh = new AuthenticatedUser(
                    userId,
                    farmUser.getFarm() == null ? null : farmUser.getFarm().getFarmId(),
                    role == null ? null : role.getRoleId(),
                    role == null ? null : role.getName(),
                    permissionCodes,
                    false);
        }

        userCache.put(userId, new CachedUser(fresh, now));
        return fresh;
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
