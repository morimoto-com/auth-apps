package com.authjava.config;

import java.util.Arrays;
import java.util.List;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import jakarta.annotation.PostConstruct;

@Configuration
public class SecurityConfig {

  @Value("${security.jwt.secret-base64}")
  private String secretBase64;

  @Value("${security.jwt.issuer:}")
  private String expectedIss;

  @Value("${security.jwt.audience:}")
  private String expectedAud;

  private SecretKey secretKey;

  @PostConstruct
  void initSecret() {
    byte[] bytes = decodeSecretFlexible(secretBase64);
    if (bytes.length < 32) {
      throw new IllegalStateException("HS256 secret must be >= 32 bytes (after decode).");
    }
    this.secretKey = new SecretKeySpec(bytes, "HmacSHA256");
  }

  private static byte[] decodeSecretFlexible(String raw) {
    if (raw == null)
      throw new IllegalStateException("security.jwt.secret-base64 is null");
    String s = raw.trim();

    // 改行や空白を除去
    s = s.replaceAll("\\s+", "");

    // まず標準Base64を試す（パディング補完あり）
    try {
      String padded = padBase64(s);
      return java.util.Base64.getDecoder().decode(padded);
    } catch (IllegalArgumentException ignored) {
      // 次に Base64URL 形式を試す
      try {
        String padded = padBase64(s);
        return java.util.Base64.getUrlDecoder().decode(padded);
      } catch (IllegalArgumentException ignored2) {
        // どちらでもない場合：プレーン文字列として扱う（最後の手段）
        // ※ 本番では Base64 を推奨。警告ログを出すなど運用で分かるように。
        return s.getBytes(java.nio.charset.StandardCharsets.UTF_8);
      }
    }
  }

  private static String padBase64(String s) {
    int mod = s.length() % 4;
    if (mod == 0)
      return s;
    // '＝' ではなく ASCII '=' を追加
    return s + "====".substring(mod);
  }

  @Bean
  JwtDecoder jwtDecoder() {
    NimbusJwtDecoder decoder = NimbusJwtDecoder
        .withSecretKey(secretKey)
        .macAlgorithm(MacAlgorithm.HS256) // HS256を明示
        .build();

    // デフォルトは exp/nbf などの基本検証のみ。iss/aud も検証したい場合は追加
    OAuth2TokenValidator<Jwt> defaultValidator = JwtValidators.createDefault(); // 時刻系など
    OAuth2TokenValidator<Jwt> withClaims = new DelegatingOAuth2TokenValidator<>(
        defaultValidator,
        (expectedIss.isBlank() ? jwt -> OAuth2TokenValidatorResult.success()
            : new JwtClaimValidator<>("iss", iss -> expectedIss.equals(iss))),
        (expectedAud.isBlank() ? jwt -> OAuth2TokenValidatorResult.success()
            : new JwtClaimValidator<List<String>>("aud",
                aud -> aud != null && aud.contains(expectedAud))));
    decoder.setJwtValidator(withClaims);
    return decoder;
  }

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http
        .csrf(csrf -> csrf.disable())
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/api/login").permitAll()
            .requestMatchers(HttpMethod.GET, "/api/userinfo").authenticated()
            .anyRequest().authenticated())
        .cors(cors -> {
        }) // ← CORSを有効化
        .oauth2ResourceServer(o -> o.jwt(jwt -> {
        }));
    return http.build();
  }

  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(Arrays.asList("http://localhost:5173")); // Vue dev server
    configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(Arrays.asList("*"));
    configuration.setAllowCredentials(true);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/api/**", configuration);
    return source;
  }
}
