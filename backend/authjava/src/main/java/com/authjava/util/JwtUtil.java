package com.authjava.util;

import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Objects;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Header;
import io.jsonwebtoken.IncorrectClaimException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.MissingClaimException;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.SignatureException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;

@Component
public class JwtUtil {

    @Value("${security.jwt.secret-base64}")
    private String secretBase64;

    @Value("${security.jwt.issuer:authjava}")
    private String issuer;

    @Value("${security.jwt.audience:api://authjava}")
    private String audience;

    @Value("${security.jwt.exp-seconds:3600}")
    private long expSeconds;

    private static final long CLOCK_SKEW_SECONDS = 60; // 許容する時計ずれ

    private SecretKey key;
    private JwtParser parser;

    @PostConstruct
    void init() {
        byte[] decoded = Base64.getDecoder().decode(secretBase64.trim());
        if (decoded.length < 32) {
            throw new IllegalStateException("HS256 secret must be >= 32 bytes after Base64 decode");
        }
        this.key = Keys.hmacShaKeyFor(decoded);

        this.parser = Jwts.parserBuilder()
                .setSigningKey(key)
                .requireIssuer(issuer)
                .requireAudience(audience)
                .setAllowedClockSkewSeconds(CLOCK_SKEW_SECONDS)
                .build();
    }

    // ============ 発行 ============
    public String generateToken(String subject) {
        Instant now = Instant.now();
        return Jwts.builder()
                .setHeaderParam(Header.TYPE, Header.JWT_TYPE) // typ: "JWT"
                .setSubject(Objects.requireNonNull(subject))
                .setIssuer(issuer)
                .setAudience(audience)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(now.plusSeconds(expSeconds)))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    // ============ 検証 ============
    public VerificationResult validate(String tokenOrBearer) {
        String token = stripBearer(tokenOrBearer);
        try {
            Jws<Claims> jws = parser.parseClaimsJws(token);
            Claims c = jws.getBody();
            return VerificationResult.ok(c);
        } catch (ExpiredJwtException e) {
            return VerificationResult.error("TOKEN_EXPIRED", e.getMessage());
        } catch (UnsupportedJwtException | MalformedJwtException e) {
            return VerificationResult.error("TOKEN_INVALID", e.getMessage());
        } catch (SignatureException e) {
            return VerificationResult.error("SIGNATURE_INVALID", e.getMessage());
        } catch (MissingClaimException | IncorrectClaimException e) {
            return VerificationResult.error("CLAIM_INVALID", e.getMessage());
        } catch (IllegalArgumentException e) {
            return VerificationResult.error("TOKEN_EMPTY", e.getMessage());
        }
    }

    // ============ ユーティリティ ============
    private static String stripBearer(String tokenOrBearer) {
        if (tokenOrBearer == null)
            return null;
        String t = tokenOrBearer.trim();
        if (t.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return t.substring(7).trim();
        }
        return t;
    }

    // 検証結果DTO
    public static class VerificationResult {
        public final boolean valid;
        public final Claims claims;
        public final String errorCode;
        public final String errorMessage;

        private VerificationResult(boolean valid, Claims claims, String errorCode, String errorMessage) {
            this.valid = valid;
            this.claims = claims;
            this.errorCode = errorCode;
            this.errorMessage = errorMessage;
        }

        public static VerificationResult ok(Claims claims) {
            return new VerificationResult(true, claims, null, null);
        }

        public static VerificationResult error(String code, String message) {
            return new VerificationResult(false, null, code, message);
        }
    }
}
