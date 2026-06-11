package com.example.project_211.config;

import com.example.project_211.security.*;
import com.example.project_211.service.TokenBlacklistService;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtUtil jwtUtil;
    private final CustomUserDetailsService userDetailsService;
    private final TokenBlacklistService tokenBlacklistService;
    private final JwtAuthEntryPoint jwtAuthEntryPoint;
    private final CustomAccessDeniedHandler accessDeniedHandler;
    private final ObjectMapper objectMapper;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);     // SRS: BCrypt strength >= 10
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        // Filter tu khoi tao (khong phai @Component) - xem giai thich o Buoc 3.1
        JwtRequestFilter jwtFilter = new JwtRequestFilter(
                jwtUtil, userDetailsService, tokenBlacklistService, objectMapper);

        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s ->
                        s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))  // SRS: Stateless
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(jwtAuthEntryPoint)      // 401 JSON
                        .accessDeniedHandler(accessDeniedHandler))        // 403 JSON
                .authorizeHttpRequests(auth -> auth
                        // Logout phai dang nhap (FR-03: Authenticated) - dat TRUOC auth/**
                        .requestMatchers("/api/v1/auth/logout").authenticated()
                        // Login, register, refresh, forgot-password: Public
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        // Xem san + khung gio: Public (khach can xem truoc khi dat)
                        .requestMatchers(HttpMethod.GET, "/api/v1/courts/**",
                                "/api/v1/time-slots/**").permitAll()
                        // Doi mat khau: chi can dang nhap, role nao cung duoc (FR-10)
                        .requestMatchers("/api/v1/users/me/**").authenticated()
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/manager/**").hasAnyRole("MANAGER", "ADMIN")
                        .requestMatchers("/api/v1/customer/**").hasRole("CUSTOMER")
                        // SRS: "Ngoai may duong dan tren thi deu truy cap duoc khong can dang nhap"
                        .anyRequest().permitAll()
                )
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
