package com.xss.gatewayservice.config;

import com.xss.gatewayservice.filter.IpWhitelistFilter;
import com.xss.gatewayservice.filter.JwtAuthenticationFilter;
import com.xss.gatewayservice.filter.SentinelLoggingFilter;
import com.xss.gatewayservice.util.JwtUtil;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final IpWhitelistProperties ipWhitelistProperties;
    private final JwtUtil jwtUtil;

    private final SentinelLoggingFilter sentinelLoggingFilter;

    public SecurityConfig(IpWhitelistProperties ipWhitelistProperties, JwtUtil jwtUtil, 
                          SentinelLoggingFilter sentinelLoggingFilter) {
        this.ipWhitelistProperties = ipWhitelistProperties;
        this.jwtUtil = jwtUtil;
        this.sentinelLoggingFilter = sentinelLoggingFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.addFilterBefore(sentinelLoggingFilter,
                UsernamePasswordAuthenticationFilter.class);

        http.addFilterBefore(new IpWhitelistFilter(ipWhitelistProperties),
                UsernamePasswordAuthenticationFilter.class);

        http.addFilterBefore(new JwtAuthenticationFilter(jwtUtil),
                UsernamePasswordAuthenticationFilter.class);

        http.authorizeHttpRequests(auth -> {
            auth.requestMatchers(org.springframework.http.HttpMethod.OPTIONS, "/**").permitAll();
            auth.requestMatchers("/token").permitAll();
            auth.requestMatchers("/auth/**").permitAll();
            auth.requestMatchers("/actuator/**").permitAll();
            auth.requestMatchers("/config/**").permitAll();
            auth.requestMatchers("/error").permitAll();
            auth.requestMatchers("/api/**").permitAll();
            auth.anyRequest().permitAll();
        });

        http.sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        http.csrf(csrf -> csrf.disable());
        http.cors(cors -> cors.configurationSource(corsConfigurationSource()));

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(Arrays.asList("*"));
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(Arrays.asList("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
