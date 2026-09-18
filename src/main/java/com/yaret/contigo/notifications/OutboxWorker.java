package com.yaret.contigo.notifications;

import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OutboxWorker {
  private final JdbcTemplate jdbc;
  private final MailGateway gateway;

  public OutboxWorker(JdbcTemplate jdbc, MailGateway gateway) {
    this.jdbc = jdbc;
    this.gateway = gateway;
  }

  @Scheduled(fixedDelayString = "${app.mail.poll-delay:5000}")
  public void process() {
    jdbc.update(
        "UPDATE notification_outbox SET status='FAILED',last_error='LEASE_EXHAUSTED' WHERE status='PROCESSING' AND attempts>=5 AND lease_until<?",
        Timestamp.from(Instant.now()));
    for (int i = 0; i < 20; i++) {
      String owner = UUID.randomUUID().toString();
      Instant now = Instant.now();
      // Atomic UPDATE + OUTPUT and READPAST lease coordinate workers without holding a transaction
      // over Graph I/O.
      var rows =
          jdbc.query(
              "UPDATE TOP (1) notification_outbox WITH (UPDLOCK,READPAST,ROWLOCK) SET status='PROCESSING',lease_owner=?,lease_until=?,attempts=attempts+1 OUTPUT inserted.id,inserted.recipient,inserted.subject,inserted.body,inserted.attempts WHERE attempts<5 AND ((status='PENDING' AND next_attempt<=?) OR (status='PROCESSING' AND lease_until<?))",
              (r, n) ->
                  new Message(
                      r.getLong(1), r.getString(2), r.getString(3), r.getString(4), r.getInt(5)),
              owner,
              Timestamp.from(now.plusSeconds(120)),
              Timestamp.from(now),
              Timestamp.from(now));
      if (rows.isEmpty()) return;
      Message m = rows.getFirst();
      try {
        gateway.send(m.recipient(), m.subject(), m.body());
        jdbc.update(
            "UPDATE notification_outbox SET status='SENT',sent_at=?,lease_until=NULL,lease_owner=NULL,last_error=NULL WHERE id=? AND lease_owner=?",
            Timestamp.from(Instant.now()),
            m.id(),
            owner);
      } catch (Exception e) {
        jdbc.update(
            "UPDATE notification_outbox SET status=?,next_attempt=?,lease_until=NULL,lease_owner=NULL,last_error=? WHERE id=? AND lease_owner=?",
            m.attempts() >= 5 ? "FAILED" : "PENDING",
            Timestamp.from(Instant.now().plusSeconds(backoff(m.attempts()))),
            e.getClass().getSimpleName(),
            m.id(),
            owner);
        org.slf4j.LoggerFactory.getLogger(getClass())
            .warn(
                "notification_failed outboxId={} attempt={} errorType={}",
                m.id(),
                m.attempts(),
                e.getClass().getSimpleName());
      }
    }
  }

  public static long backoff(int attempt) {
    return Math.min(3600, 30L << Math.min(attempt - 1, 7));
  }

  record Message(long id, String recipient, String subject, String body, int attempts) {}
}
