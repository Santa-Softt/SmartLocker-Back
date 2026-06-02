package com.smartlockr.iam.infrastructure.security.configuration;

import com.smartlockr.iam.infrastructure.security.error.handler.SecurityErrorHandler;
import com.smartlockr.iam.infrastructure.security.oauth2.GoogleSuccessHandler;
import com.smartlockr.shared.properties.ApplicationProperties;
import com.smartlockr.shared.properties.SecurityProperties;
import jakarta.servlet.http.Cookie;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.util.WebUtils;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Value("${graphiql.public-access:false}")
    private boolean isPublicAccessEnabled;

    /**
     * Main security filter chain for the application.
     * Configures JWT-based Resource Server, OAuth2 Login, and stateless session management.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   BearerTokenResolver bearerTokenResolver,
                                                   JwtDecoder jwtDecoder,
                                                   JwtAuthenticationConverter jwtAuthenticationConverter,
                                                   GoogleSuccessHandler googleSuccessHandler,
                                                   SecurityErrorHandler securityErrorHandler,
                                                   SecurityProperties securityProperties) throws Exception {
        return http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> {
                    if (securityProperties.csrfEnabled()) {
                        csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                                .ignoringRequestMatchers("/api/v1/webhooks/mercadopago");
                    } else {
                        csrf.disable();
                    }
                })
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(authorize -> {
                    if (isPublicAccessEnabled)
                        authorize.requestMatchers("/graphiql", "/graphql").permitAll();
                    authorize.requestMatchers("/auth/refresh").permitAll()
                            .requestMatchers("/api/v1/webhooks/mercadopago").permitAll()
                            .requestMatchers("/actuator/health", "/actuator/prometheus").permitAll()
                            .anyRequest().authenticated();
                })

                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(securityErrorHandler)
                        .accessDeniedHandler(securityErrorHandler)
                )
                .oauth2Login(oauth2 ->
                        oauth2.successHandler(googleSuccessHandler))
                .oauth2ResourceServer(oauth2 ->
                        oauth2.jwt(jwt -> {
                                    jwt.decoder(jwtDecoder);
                                    jwt.jwtAuthenticationConverter(jwtAuthenticationConverter);
                                })
                                .bearerTokenResolver(bearerTokenResolver)
                                .authenticationEntryPoint(securityErrorHandler)
                )
                .build();
    }

    /**
     * Custom resolver that extracts the JWT token from a cookie named "auth_token"
     * instead of the standard Authorization Header.
     */
    @Bean
    public BearerTokenResolver cookieBearerTokenResolver() {
        return request -> {
            Cookie authCookie = WebUtils.getCookie(request, "auth_token");
            return (authCookie != null) ? authCookie.getValue() : null;
        };
    }

    /**
     * CORS configuration using application properties to define allowed origins,
     * allowing standard REST methods and credentials.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(ApplicationProperties appProperties) {
        CorsConfiguration configuration = new CorsConfiguration();

        configuration.setAllowedOrigins(List.copyOf(appProperties.allowedOrigins()));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        return source;
    }
}
