package com.example.agent.config;

import com.example.agent.security.Sha256PasswordEncoder;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.rememberme.TokenBasedRememberMeServices;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

@Configuration
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder(@Value("${app.security.password-iterations}") int iterations) {
        return new Sha256PasswordEncoder(iterations);
    }

    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    TokenBasedRememberMeServices rememberMeServices(
            UserDetailsService userDetailsService,
            @Value("${app.security.remember-me.key}") String key,
            @Value("${app.security.remember-me.validity-days:30}") int validityDays,
            @Value("${app.security.remember-me.secure:false}") boolean secure) {
        var services = new TokenBasedRememberMeServices(key, userDetailsService);
        services.setAlwaysRemember(true);
        services.setCookieName("SONORA_REMEMBER");
        services.setTokenValiditySeconds(Math.multiplyExact(validityDays, 24 * 60 * 60));
        services.setUseSecureCookie(secure);
        return services;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityContextRepository repository,
                                            ObjectMapper objectMapper,
                                            UrlBasedCorsConfigurationSource corsSource,
                                            TokenBasedRememberMeServices rememberMeServices) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsSource))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/auth/csrf", "/api/auth/captcha", "/api/auth/register", "/api/auth/login")
                        .permitAll()
                        .anyRequest().authenticated())
                .securityContext(context -> context
                        .securityContextRepository(repository)
                        .requireExplicitSave(true))
                .sessionManagement(session -> session.sessionFixation(fixation -> fixation.migrateSession()))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) -> {
                            writeSecurityError(response, objectMapper, HttpServletResponse.SC_UNAUTHORIZED, "请先登录");
                        })
                        .accessDeniedHandler((request, response, exception) -> {
                            writeSecurityError(response, objectMapper, HttpServletResponse.SC_FORBIDDEN, "没有权限执行此操作");
                        }))
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .rememberMe(remember -> remember.rememberMeServices(rememberMeServices))
                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        .addLogoutHandler(rememberMeServices::logout)
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("JSESSIONID", "SONORA_REMEMBER")
                        .logoutSuccessHandler((request, response, authentication) -> {
                            response.setStatus(HttpServletResponse.SC_OK);
                            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            objectMapper.writeValue(response.getWriter(),
                                    java.util.Map.of("success", true, "message", "已安全退出登录"));
                        }));
        return http.build();
    }

    @Bean
    UrlBasedCorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins:http://localhost:5173,http://127.0.0.1:5173}") String allowedOrigins) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Content-Type", "X-CSRF-TOKEN", "X-XSRF-TOKEN"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    private static void writeSecurityError(HttpServletResponse response, ObjectMapper objectMapper,
                                           int status, String message) throws java.io.IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), java.util.Map.of("success", false, "message", message));
    }
}
