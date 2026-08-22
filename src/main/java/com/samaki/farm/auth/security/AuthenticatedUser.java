package com.samaki.farm.auth.security;

import lombok.Value;
import java.util.List;
import java.util.UUID;

/**
 * Taarifa za mtumiaji aliyeingia - userId/isRoot vinatoka JWT claims, lakini
 * farmId/roleId/roleName/permissions vinasomwa FRESH kutoka DB kwenye kila
 * request (kwa cache fupi - angalia JwtAuthFilter), si kutoka JWT moja kwa
 * moja, ili mabadiliko ya role/ruhusa yaanze kufanya kazi papo hapo bila
 * mtumiaji kulazimika ku-login tena (kama Lsms AuthTokenFilter).
 *
 * Inatumika NA REST controllers NA GraphQL resolvers - chanzo kimoja cha
 * ukweli wa RBAC kwa API zote mbili (angalia SecurityConfig na GraphQLContextFilter).
 */
@Value
public class AuthenticatedUser {
    UUID userId;
    Integer farmId;
    Integer roleId;
    String roleName;
    List<String> permissions;

    // ROOT bypass flag (kama Lsms isRoot) - HURU na jina la role, hivyo role
    // za kawaida (mfano "OWNER") zinaweza kubadilishwa/kufutwa bila kuvunja
    // ufikiaji wa msimamizi mkuu wa mfumo.
    boolean root;

    public boolean hasPermission(String code) {
        return root || (permissions != null && permissions.contains(code));
    }
}
