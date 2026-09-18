package com.yaret.contigo.auth;

import com.yaret.contigo.shared.AppException;
import java.sql.Timestamp;
import java.time.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LoginGuard {
  private final JdbcTemplate jdbc;

  public LoginGuard(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  // DB-backed buckets coordinate throttling across instances. Credentials and raw IPs are never
  // persisted.
  @Transactional
  public void attempt(String key) {
    var rows =
        jdbc.query(
            "SELECT attempts, window_start FROM login_attempts WITH (UPDLOCK,HOLDLOCK) WHERE bucket=?",
            (r, n) -> new Bucket(r.getInt(1), r.getTimestamp(2).toInstant()),
            key);
    Instant now = Instant.now();
    if (rows.isEmpty()) {
      jdbc.update(
          "INSERT INTO login_attempts(bucket,attempts,window_start) VALUES (?,1,?)",
          key,
          Timestamp.from(now));
      return;
    }
    Bucket b = rows.getFirst();
    if (b.start().plusSeconds(900).isBefore(now))
      jdbc.update(
          "UPDATE login_attempts SET attempts=1,window_start=? WHERE bucket=?",
          Timestamp.from(now),
          key);
    else {
      if (b.attempts() >= 10)
        throw new AppException(429, "Demasiados intentos. Espere 15 minutos.");
      jdbc.update("UPDATE login_attempts SET attempts=attempts+1 WHERE bucket=?", key);
    }
  }

  record Bucket(int attempts, Instant start) {}
}
