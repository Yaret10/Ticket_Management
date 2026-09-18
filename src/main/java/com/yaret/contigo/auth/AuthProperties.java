package com.yaret.contigo.auth;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;

@ConfigurationProperties("app.auth")
public record AuthProperties(
    Resource privateKey,
    Resource publicKey,
    String issuer,
    String audience,
    Duration accessTtl,
    Duration refreshTtl,
    boolean secureCookies) {}
