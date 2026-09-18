package com.yaret.contigo.notifications;

public interface MailGateway {
  void send(String recipient, String subject, String body);
}
