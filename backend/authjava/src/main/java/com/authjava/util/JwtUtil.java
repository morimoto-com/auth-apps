package com.authjava.util;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Objects;

public class JwtUtil {

    // ====== 設定値（環境変数や設定ファイルから注入推奨）======
    // HS256 では 少なくとも 32 bytes (256-bit) 以上の強度を確保
    private static final String SECRET = System.getenv().getOrDefault(
            "JWT_HS256_SECRET",
            "CHANGE_ME_to_32+_bytes_random_secret________________________________"
    );
    private static final String ISSUER = System.getenv().getOrDefault("JWT_ISSUER", "authjava");
    private static final String AUDIENCE = System.getenv().getOrDefault("JWT_AUDIENCE", "api://authjava");
    private static final long EXP_SECONDS = Long.parseLong(System.getenv().getOrDefault("JWT_EXP_SECONDS", "3600"));
    private static final long CLOCK_SKEW_SECONDS = 60; // 許容する時計ずれ

    private static final SecretKey KEY = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));

    private static JwtParser parser() {
        return Jwts.parserBuilder()
                .setSigningKey(KEY)
                .requireIssuer(ISSUER)
                .requireAudience(AUDIENCE)
                .setAllowedClockSkewSeconds(CLOCK_SKEW_SECONDS)
                .build();
    }

    // ============ 発行 ============
    public static String generateToken(String subject) {
        Instant now = Instant.now();
        return Jwts.builder()
                .setHeaderParam(Header.TYPE, Header.JWT_TYPE)     // typ: "JWT"
                .setSubject(Objects.requireNonNull(subject))
                .setIssuer(ISSUER)
                .setAudience(AUDIENCE)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(now.plusSeconds(EXP_SECONDS)))
                // .claim("roles", List.of("USER"))  // 役割を積みたい場合の例
                .signWith(KEY, SignatureAlgorithm.HS256)          // アルゴリズムを明示
                .compact();
    }

    // ============ 検証 ============
    public static VerificationResult validate(String tokenOrBearer) {
        String token = stripBearer(tokenOrBearer);
        try {
            Jws<Claims> jws = parser().parseClaimsJws(token);
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
    public static String stripBearer(String tokenOrBearer) {
        if (tokenOrBearer == null) return null;
        String t = tokenOrBearer.trim();
        if (t.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return t.substring(7).trim();
        }
        return t;
    }

    // 検証結果を扱いやすくする小さなDTO
    public static class VerificationResult {
        public final boolean valid;
        public final Claims claims;      // sub, iss, aud, exp などにアクセス可
        public final String errorCode;   // "TOKEN_EXPIRED" 等
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
