package com.yaret.contigo.notifications;

import com.yaret.contigo.tickets.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {
  private final OutboxRepository outbox;
  private final JdbcTemplate jdbc;
  private final String publicUrl;
  private final String ti;
  private final String cc;

  public NotificationService(
      OutboxRepository outbox,
      JdbcTemplate jdbc,
      @Value("${app.public-url}") String publicUrl,
      @Value("${app.mail.ti-addresses:}") String ti,
      @Value("${app.mail.default-cc:}") String cc) {
    this.outbox = outbox;
    this.jdbc = jdbc;
    this.publicUrl = publicUrl;
    this.ti = ti;
    this.cc = cc;
  }

  public void enqueue(Ticket t, boolean created) {
    Set<String> recipients = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
    recipients.add(t.requester.getEmail());
    if (created)
      recipients.addAll(
          jdbc.queryForList(
              "SELECT email FROM area_notification_recipients WHERE area_id=?",
              String.class,
              t.area.getId()));
    if (t.state == TicketState.APROBADO) {
      recipients.addAll(
          jdbc.queryForList(
              "SELECT DISTINCT u.email FROM app_users u JOIN user_roles r ON r.user_id=u.id WHERE r.role='TI' AND u.enabled=1",
              String.class));
      recipients.addAll(Arrays.asList(ti.split(",")));
      recipients.addAll(Arrays.asList(cc.split(",")));
    }
    for (String email : recipients)
      if (email != null && !email.isBlank()) {
        OutboxMessage m = new OutboxMessage();
        m.recipient = email.trim();
        m.subject = "[conTIgo] " + t.code + " — " + t.state;
        m.body =
            "Su ticket "
                + t.code
                + " está en estado "
                + t.state
                + ". Consulte el detalle e informe técnico en "
                + publicUrl
                + "/tickets/"
                + t.id
                + ". No responda a este mensaje.";
        outbox.save(m);
      }
  }
}
