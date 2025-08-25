import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http
        .csrf(csrf -> csrf.disable()) // フロントと連携するならまず無効化
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/api/login").permitAll()
            .anyRequest().authenticated())
        .cors(cors -> {
        }); // ← cors を有効化

    return http.build();
  }
}
