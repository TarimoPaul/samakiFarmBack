package com.samaki.farm.auth.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * JWT inabeba TU utambulisho (userId + isRoot) na muktadha wa kuonyesha UI
 * (farmId/roleId/roleName) - SI orodha ya ruhusa (kama ilivyokuwa awali).
 * Ruhusa halisi zinasomwa fresh kutoka DB kwenye kila request na JwtAuthFilter,
 * ili mabadiliko ya role/ruhusa yasilazimu re-login (kama Lsms).
 */
@Component
public class JwtUtil {

    @Value("${app.jwt.secret}")
    private String secret;

    @Value("${app.jwt.expiration-hours}")
    private long expirationHours;

    private SecretKey key() {
        return Keys.hmacShaKeyFor(secret.getBytes());
    }

    public String generateToken(UUID userId, Integer farmId, Integer roleId, String roleName, boolean isRoot) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId.toString())
                .claim("farmId", farmId)
                .claim("roleId", roleId)
                .claim("roleName", roleName)
                .claim("isRoot", isRoot)
                .issuedAt(java.util.Date.from(now))
                .expiration(java.util.Date.from(now.plus(expirationHours, ChronoUnit.HOURS)))
                .signWith(key())
                .compact();
    }

    public Claims parseToken(String token) {
        return Jwts.parser().verifyWith(key()).build().parseSignedClaims(token).getPayload();
    }
}
