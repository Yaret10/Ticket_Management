package com.yaret.contigo.auth;

import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import jakarta.servlet.http.*;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.*;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.NullSecurityContextRepository;
import org.springframework.security.web.csrf.*;
import org.springframework.security.web.savedrequest.NullRequestCache;

@Configuration
@EnableConfigurationProperties(AuthProperties.class)
public class SecurityConfiguration {
  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12);
  }

  @Bean
  JwtEncoder encoder(AuthProperties p) throws Exception {
    var privateKey = RsaKeyConverters.pkcs8().convert(keyStream(p.privateKey(), "private"));
    var publicKey = RsaKeyConverters.x509().convert(keyStream(p.publicKey(), "public"));
    var key = new RSAKey.Builder(publicKey).privateKey(privateKey).keyID("contigo-signing").build();
    return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
  }

  @Bean
  JwtDecoder decoder(AuthProperties p) throws Exception {
    var key = RsaKeyConverters.x509().convert(keyStream(p.publicKey(), "public"));
    var decoder =
        NimbusJwtDecoder.withPublicKey(key).signatureAlgorithm(SignatureAlgorithm.RS256).build();
    OAuth2TokenValidator<Jwt> audience =
        jwt ->
            jwt.getAudience().contains(p.audience())
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"));
    decoder.setJwtValidator(
        new DelegatingOAuth2TokenValidator<>(
            JwtValidators.createDefaultWithIssuer(p.issuer()),
            new JwtTimestampValidator(java.time.Duration.ZERO),
            new JwtClaimValidator<java.time.Instant>("exp", Objects::nonNull),
            audience));
    return decoder;
  }

  /**
   * Accepts either a Resource location (local/Docker) or the PEM text itself (App Service).
   * Environment variables may contain literal \\n sequences; normalize them before parsing.
   */
  private static InputStream keyStream(String value, String kind) {
    if (value == null || value.isBlank()) {
      throw new IllegalStateException("Falta la clave JWT " + kind + ".");
    }
    String normalized = value.trim().replace("\\n", "\n");
    if (normalized.startsWith("-----BEGIN")) {
      return new ByteArrayInputStream(normalized.getBytes(StandardCharsets.UTF_8));
    }
    try {
      return new DefaultResourceLoader().getResource(normalized).getInputStream();
    } catch (Exception ex) {
      throw new IllegalStateException(
          "No se pudo leer la clave JWT " + kind + ". Use PEM o una ruta file:/ vÃ¡lida.", ex);
    }
  }

  @Bean
  BearerTokenResolver cookieResolver() {
    return request -> {
      // Public recovery/auth endpoints must remain usable even with an expired cookie.
      String path = request.getRequestURI();
      if (Set.of(
                  "/login",
                  "/auth/recover",
                  "/api/v1/auth/login",
                  "/api/v1/auth/refresh",
                  "/api/v1/auth/logout",
                  "/api/v1/auth/csrf")
              .contains(path)
          || path.startsWith("/css/")
          || path.startsWith("/js/")
          || path.startsWith("/images/")) return null;
      if (request.getCookies() != null)
        for (var c : request.getCookies()) if ("ACCESS".equals(c.getName())) return c.getValue();
      return null;
    };
  }

  @Bean
  SecurityFilterChain security(
      HttpSecurity http, BearerTokenResolver resolver, AuthProperties properties) throws Exception {
    var csrf = CookieCsrfTokenRepository.withHttpOnlyFalse();
    csrf.setCookiePath("/");
    csrf.setCookieCustomizer(c -> c.secure(properties.secureCookies()).sameSite("Lax"));
    http.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .securityContext(c -> c.securityContextRepository(new NullSecurityContextRepository()))
        .requestCache(c -> c.requestCache(new NullRequestCache()))
        .csrf(
            c ->
                c.csrfTokenRepository(csrf)
                    .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                    .withObjectPostProcessor(
                        new org.springframework.security.config.ObjectPostProcessor<CsrfFilter>() {
                          @Override
                          public <O extends CsrfFilter> O postProcess(O filter) {
                            // Resource Server normally exempts bearer requests. Cookie JWTs MUST
                            // remain subject to CSRF.
                            filter.setRequireCsrfProtectionMatcher(CsrfFilter.DEFAULT_CSRF_MATCHER);
                            return filter;
                          }
                        }))
        .authorizeHttpRequests(
            a ->
                a.dispatcherTypeMatchers(jakarta.servlet.DispatcherType.ASYNC)
                    .permitAll()
                    .requestMatchers(
                        "/login",
                        "/auth/recover",
                        "/css/**",
                        "/js/**",
                        "/images/**",
                        "/api/v1/auth/login",
                        "/api/v1/auth/refresh",
                        "/api/v1/auth/logout",
                        "/api/v1/auth/csrf",
                        "/actuator/health",
                        "/error")
                    .permitAll()
                    .requestMatchers("/actuator/prometheus")
                    .hasRole("TI")
                    .requestMatchers("/actuator/**")
                    .denyAll()
                    .anyRequest()
                    .authenticated())
        .oauth2ResourceServer(
            o ->
                o.jwt(
                        j -> {
                          var authorities =
                              new org.springframework.security.oauth2.server.resource.authentication
                                  .JwtGrantedAuthoritiesConverter();
                          authorities.setAuthoritiesClaimName("roles");
                          authorities.setAuthorityPrefix("ROLE_");
                          var converter =
                              new org.springframework.security.oauth2.server.resource.authentication
                                  .JwtAuthenticationConverter();
                          converter.setJwtGrantedAuthoritiesConverter(authorities);
                          j.jwtAuthenticationConverter(converter);
                        })
                    .bearerTokenResolver(resolver)
                    .authenticationEntryPoint(SecurityConfiguration::unauthenticated))
        .exceptionHandling(
            e ->
                e.authenticationEntryPoint(SecurityConfiguration::unauthenticated)
                    .accessDeniedHandler(
                        (req, res, ex) ->
                            problem(res, 403, "No tiene permiso o falta el token CSRF.")))
        .headers(
            h ->
                h.contentSecurityPolicy(
                    c ->
                        c.policyDirectives(
                            "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; object-src 'none'; base-uri 'self'; frame-ancestors 'none'")));
    return http.build();
  }

  private static void unauthenticated(
      HttpServletRequest req,
      HttpServletResponse res,
      org.springframework.security.core.AuthenticationException e)
      throws java.io.IOException {
    if (!req.getRequestURI().startsWith("/api/")
        && !req.getRequestURI().startsWith("/v3/")
        && !req.getRequestURI().startsWith("/swagger")) {
      String destination =
          req.getRequestURI() + (req.getQueryString() == null ? "" : "?" + req.getQueryString());
      res.sendRedirect(
          "/auth/recover?next="
              + java.net.URLEncoder.encode(destination, java.nio.charset.StandardCharsets.UTF_8));
    } else problem(res, 401, "Su autenticaciÃ³n ha vencido. Inicie sesiÃ³n nuevamente.");
  }

  static void problem(HttpServletResponse res, int status, String detail)
      throws java.io.IOException {
    res.setStatus(status);
    res.setContentType("application/problem+json");
    res.setCharacterEncoding("UTF-8");
    res.getWriter()
        .write(
            "{\"type\":\"about:blank\",\"status\":" + status + ",\"detail\":\"" + detail + "\"}");
  }
}
