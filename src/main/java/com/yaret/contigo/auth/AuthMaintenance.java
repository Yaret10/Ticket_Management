package com.yaret.contigo.auth;

import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AuthMaintenance {
  private final JdbcTemplate jdbc;

  public AuthMaintenance(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Scheduled(fixedDelay = 3600000, initialDelay = 3600000)
  public void clean() {
    jdbc.update(
        "DELETE FROM login_attempts WHERE window_start<?",
        Timestamp.from(Instant.now().minusSeconds(86400)));
    jdbc.update(
        "DELETE FROM refresh_families WHERE expires_at<? AND NOT EXISTS (SELECT 1 FROM refresh_tokens t WHERE t.family=refresh_families.id)",
        Timestamp.from(Instant.now().minusSeconds(86400)));
    // Keep consumed refresh tokens for the entire family lifetime to detect replay.
    jdbc.update(
        "DELETE FROM refresh_tokens WHERE family IN (SELECT family FROM refresh_tokens GROUP BY family HAVING MAX(expires_at)<?)",
        Timestamp.from(Instant.now().minusSeconds(86400)));
  }
}
