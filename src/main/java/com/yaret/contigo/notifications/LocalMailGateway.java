package com.yaret.contigo.notifications;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.mail.mode", havingValue = "local", matchIfMissing = true)
public class LocalMailGateway implements MailGateway {
  public void send(String recipient, String subject, String body) {
    org.slf4j.LoggerFactory.getLogger(getClass()).info("local_notification_simulated");
  }
}
