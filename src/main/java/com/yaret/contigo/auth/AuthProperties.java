package com.yaret.contigo.auth;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.auth")
public record AuthProperties(
    String privateKey,
    String publicKey,
    String issuer,
    String audience,
    Duration accessTtl,
    Duration refreshTtl,
    boolean secureCookies) {}
