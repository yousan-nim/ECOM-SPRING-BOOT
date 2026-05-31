package com.ecom.user.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.time.Instant;

@Configuration
@EnableMethodSecurity   // ⇒ @PreAuthorize / @PostAuthorize on methods
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final ObjectMapper            objectMapper;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthFilter, ObjectMapper objectMapper) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.objectMapper  = objectMapper;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);   // ~250 ms / hash → resists brute force
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration cfg) throws Exception {
        return cfg.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())                          // REST API, no cookies-driven CSRF
            .cors(cors -> {})                                      // pick up CorsConfigurationSource if present
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Public — auth flow itself.
                .requestMatchers(HttpMethod.POST,
                        "/api/v1/auth/register",
                        "/api/v1/auth/login",
                        "/api/v1/auth/refresh",
                        "/api/v1/auth/logout").permitAll()

                // Public — system / docs.
                .requestMatchers("/api/v1/ping",
                                 "/actuator/health/**",
                                 "/actuator/info",
                                 "/v3/api-docs/**",
                                 "/swagger-ui/**",
                                 "/swagger-ui.html").permitAll()

                // Coarse role gates — fine-grained checks via @PreAuthorize on methods.
                .requestMatchers("/api/v1/admin/**").hasRole("PLATFORM_ADMIN")
                .requestMatchers("/api/v1/vendor/**").hasAnyRole("VENDOR_ADMIN", "PLATFORM_ADMIN")

                .anyRequest().authenticated())
            .exceptionHandling(eh -> eh
                .authenticationEntryPoint((req, res, ex) ->
                        writeProblem(res, HttpServletResponse.SC_UNAUTHORIZED,
                                "Authentication required",
                                "AUTHENTICATION_REQUIRED", req.getRequestURI()))
                .accessDeniedHandler((req, res, ex) ->
                        writeProblem(res, HttpServletResponse.SC_FORBIDDEN,
                                "Access denied",
                                "ACCESS_DENIED", req.getRequestURI())))
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /** Write an RFC-7807 problem+json body for unauthenticated / forbidden requests. */
    private void writeProblem(HttpServletResponse res, int status, String detail,
                              String code, String instance) throws java.io.IOException {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                org.springframework.http.HttpStatusCode.valueOf(status), detail);
        pd.setType(URI.create("https://api.ecom.dev/errors/" + code.toLowerCase().replace('_', '-')));
        pd.setTitle(status == 401 ? "Unauthorized" : "Forbidden");
        pd.setInstance(URI.create(instance));
        pd.setProperty("code", code);
        pd.setProperty("timestamp", Instant.now().toString());

        res.setStatus(status);
        res.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(res.getOutputStream(), pd);
    }
}
