package com.yaret.contigo.auth;

import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Duration;
import java.util.Map;
import org.springframework.http.*;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@io.swagger.v3.oas.annotations.security.SecurityRequirements
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
  public record Login(
      @NotBlank @Pattern(regexp = "[0-9]{8}") String dni,
      @NotBlank @Size(max = 72) String password) {}

  private final TokenService tokens;
  private final AuthProperties config;
  private final LoginGuard guard;

  public AuthController(TokenService tokens, AuthProperties config, LoginGuard guard) {
    this.tokens = tokens;
    this.config = config;
    this.guard = guard;
  }

  @GetMapping("/csrf")
  public Map<String, String> csrf(CsrfToken token) {
    return Map.of("token", token.getToken(), "headerName", token.getHeaderName());
  }

  @PostMapping("/login")
  public Map<String, String> login(
      @Valid @RequestBody Login d, HttpServletRequest req, HttpServletResponse res) {
    guard.attempt(TokenService.hash("dni:" + d.dni()));
    guard.attempt(TokenService.hash("ip:" + req.getRemoteAddr()));
    set(res, tokens.login(d.dni(), d.password()));
    return Map.of("message", "Sesión iniciada.");
  }

  @PostMapping("/refresh")
  public Map<String, String> refresh(
      @CookieValue(name = "REFRESH", required = false) String raw, HttpServletResponse res) {
    try {
      set(res, tokens.rotate(raw));
      return Map.of("message", "Autenticación renovada.");
    } catch (com.yaret.contigo.shared.AppException e) {
      clear(res);
      throw e;
    }
  }

  @PostMapping("/logout")
  public Map<String, String> logout(
      @CookieValue(name = "REFRESH", required = false) String raw, HttpServletResponse res) {
    tokens.logout(raw);
    clear(res);
    return Map.of("message", "Sesión cerrada.");
  }

  private void set(HttpServletResponse res, TokenService.Tokens t) {
    cookie(res, "ACCESS", t.access(), "/", config.accessTtl());
    cookie(res, "REFRESH", t.refresh(), "/api/v1/auth", config.refreshTtl());
  }

  private void clear(HttpServletResponse res) {
    cookie(res, "ACCESS", "", "/", Duration.ZERO);
    cookie(res, "REFRESH", "", "/api/v1/auth", Duration.ZERO);
  }

  private void cookie(
      HttpServletResponse res, String name, String value, String path, Duration age) {
    res.addHeader(
        HttpHeaders.SET_COOKIE,
        ResponseCookie.from(name, value)
            .httpOnly(true)
            .secure(config.secureCookies())
            .sameSite("Lax")
            .path(path)
            .maxAge(age)
            .build()
            .toString());
    res.setHeader("Cache-Control", "no-store");
  }
}
