package com.example.demo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable()) // API 서버이므로 CSRF 비활성화
                .cors(cors -> cors.configurationSource(corsConfigurationSource())) // [수정] 정의한 CORS 설정이 올바르게 적용되도록 연결
                .authorizeHttpRequests(auth -> auth
                        // [추가] 렌더 헬스체크 및 기본 루트("/") 경로 허용 (이게 없으면 렌더가 타임아웃으로 서버를 죽입니다!)
                        .requestMatchers("/", "/error", "/favicon.ico").permitAll()
                        // 기존에 허용하셨던 API 경로들
                        .requestMatchers("/api/members/**", "/api/production/**", "/api/hr/**").permitAll()
                        .anyRequest().authenticated()
                );
        
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        
        // 프론트엔드 로컬 주소 + 필요시 배포된 프론트엔드 주소 추가 가능
        configuration.setAllowedOrigins(List.of(
                "http://localhost:3000", 
                "http://127.0.0.1:3000"
                // 만약 프론트엔드도 렌더나 Vercel 등에 배포했다면 여기에 해당 도메인 추가 가능
        ));
        
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}