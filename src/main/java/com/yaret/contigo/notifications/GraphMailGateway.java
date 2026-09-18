package com.yaret.contigo.notifications;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

@Component
@ConditionalOnProperty(name = "app.mail.mode", havingValue = "graph")
public class GraphMailGateway implements MailGateway {
  private final RestClient client;
  private final String tenant, id, secret, sender;

  public GraphMailGateway(
      @Value("${app.mail.tenant-id}") String tenant,
      @Value("${app.mail.client-id}") String id,
      @Value("${app.mail.client-secret}") String secret,
      @Value("${app.mail.sender}") String sender) {
    this.tenant = tenant;
    this.id = id;
    this.secret = secret;
    this.sender = sender;
    if (!tenant.matches("[a-zA-Z0-9-]{1,100}")
        || id.isBlank()
        || secret.isBlank()
        || sender.isBlank()) throw new IllegalStateException("Configure Microsoft Graph.");
    var factory =
        new JdkClientHttpRequestFactory(
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
    factory.setReadTimeout(Duration.ofSeconds(30));
    client = RestClient.builder().requestFactory(factory).build();
  }

  public void send(String recipient, String subject, String body) {
    var form = new LinkedMultiValueMap<String, String>();
    form.add("client_id", id);
    form.add("client_secret", secret);
    form.add("scope", "https://graph.microsoft.com/.default");
    form.add("grant_type", "client_credentials");
    var token =
        client
            .post()
            .uri("https://login.microsoftonline.com/" + tenant + "/oauth2/v2.0/token")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(form)
            .retrieve()
            .body(Map.class);
    if (token == null || !(token.get("access_token") instanceof String access))
      throw new IllegalStateException("GRAPH_AUTH_FAILED");
    var payload =
        Map.of(
            "message",
            Map.of(
                "subject",
                subject,
                "body",
                Map.of("contentType", "Text", "content", body),
                "toRecipients",
                List.of(Map.of("emailAddress", Map.of("address", recipient)))),
            "saveToSentItems",
            true);
    client
        .post()
        .uri("https://graph.microsoft.com/v1.0/users/{sender}/sendMail", sender)
        .headers(h -> h.setBearerAuth(access))
        .body(payload)
        .retrieve()
        .toBodilessEntity();
  }
}
