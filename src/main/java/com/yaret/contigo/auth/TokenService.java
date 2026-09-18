package com.yaret.contigo.auth;

import com.yaret.contigo.shared.AppException;
import com.yaret.contigo.users.*;
import java.security.*;
import java.time.*;
import java.util.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TokenService {
  public record Tokens(String access, String refresh) {}

  private final UserRepository users;
  private final RefreshRepository refresh;
  private final FamilyRepository families;
  private final PasswordEncoder encoder;
  private final JwtEncoder jwt;
  private final AuthProperties config;
  private final String dummyHash;

  public TokenService(
      UserRepository users,
      RefreshRepository refresh,
      FamilyRepository families,
      PasswordEncoder encoder,
      JwtEncoder jwt,
      AuthProperties config) {
    this.users = users;
    this.refresh = refresh;
    this.families = families;
    this.encoder = encoder;
    this.jwt = jwt;
    this.config = config;
    dummyHash = encoder.encode(UUID.randomUUID().toString());
  }

  @Transactional
  public Tokens login(String dni, String password) {
    if (password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) throw invalid();
    User user = users.findByDni(dni).orElse(null);
    boolean valid = encoder.matches(password, user == null ? dummyHash : user.passwordHash);
    if (!valid || user == null || !user.enabled) throw invalid();
    RefreshFamily family = new RefreshFamily();
    family.id = UUID.randomUUID().toString();
    family.user = user;
    family.createdAt = Instant.now();
    family.expiresAt = family.createdAt.plus(config.refreshTtl());
    return issue(user, families.saveAndFlush(family));
  }

  // A reuse rejection MUST commit family revocation rather than roll it back.
  @Transactional(noRollbackFor = AppException.class)
  public Tokens rotate(String raw) {
    if (raw == null || raw.length() > 200) throw invalid();
    String digest = hash(raw);
    // Read only the family identifier before locking; do not cache a stale token entity while
    // waiting.
    String familyId = refresh.familyOfHash(digest).orElseThrow(TokenService::invalid);
    RefreshFamily family = families.lock(familyId).orElseThrow(TokenService::invalid);
    RefreshToken token = refresh.lock(digest).orElseThrow(TokenService::invalid);
    if (family.revoked || token.usedAt != null || token.revoked) {
      revoke(family);
      throw invalid();
    }
    if (token.expiresAt.isBefore(Instant.now()) || !token.user.isEnabled()) {
      revoke(family);
      throw invalid();
    }
    token.usedAt = Instant.now();
    refresh.saveAndFlush(token);
    return issue(token.user, family);
  }

  @Transactional
  public void logout(String raw) {
    if (raw != null && raw.length() <= 200)
      refresh.familyOfHash(hash(raw)).flatMap(families::lock).ifPresent(this::revoke);
  }

  private void revoke(RefreshFamily family) {
    family.revoked = true;
    families.saveAndFlush(family);
    refresh.revokeFamily(family.id);
  }

  private Tokens issue(User user, RefreshFamily family) {
    Instant now = Instant.now();
    var claims =
        JwtClaimsSet.builder()
            .issuer(config.issuer())
            .audience(List.of(config.audience()))
            .subject(user.getId().toString())
            .claim("roles", user.getRoles().stream().map(Enum::name).toList())
            .issuedAt(now)
            .expiresAt(now.plus(config.accessTtl()))
            .id(UUID.randomUUID().toString())
            .build();
    String access =
        jwt.encode(
                JwtEncoderParameters.from(
                    JwsHeader.with(SignatureAlgorithm.RS256).keyId("contigo-signing").build(),
                    claims))
            .getTokenValue();
    byte[] entropy = new byte[32];
    new SecureRandom().nextBytes(entropy);
    String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(entropy);
    RefreshToken t = new RefreshToken();
    t.tokenHash = hash(raw);
    t.user = user;
    t.family = family.id;
    t.expiresAt = now.plus(config.refreshTtl());
    family.expiresAt = t.expiresAt;
    families.saveAndFlush(family);
    refresh.saveAndFlush(t);
    return new Tokens(access, raw);
  }

  public static String hash(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private static AppException invalid() {
    return new AppException(401, "Credenciales inválidas o autenticación vencida.");
  }
}
