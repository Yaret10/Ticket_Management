package com.yaret.contigo.notifications;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.sql.Timestamp;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class OutboxWorkerTest {
  @Test
  void providerFailureSchedulesRetryWithoutSendingAgainImmediately() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    MailGateway gateway = mock(MailGateway.class);
    when(jdbc.query(
            anyString(),
            org.mockito.ArgumentMatchers.<RowMapper<OutboxWorker.Message>>any(),
            any(Object[].class)))
        .thenReturn(
            List.of(new OutboxWorker.Message(42, "test@example.invalid", "Subject", "Body", 1)),
            List.of());
    doThrow(new IllegalStateException("Provider failure"))
        .when(gateway)
        .send(anyString(), anyString(), anyString());
    new OutboxWorker(jdbc, gateway).process();
    verify(gateway, times(1)).send(anyString(), anyString(), anyString());
    verify(jdbc)
        .update(
            contains("next_attempt"),
            eq("PENDING"),
            any(Timestamp.class),
            eq("IllegalStateException"),
            eq(42L),
            anyString());
  }

  @Test
  void lastFailureMovesNotificationToFailed() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    MailGateway gateway = mock(MailGateway.class);
    when(jdbc.query(
            anyString(),
            org.mockito.ArgumentMatchers.<RowMapper<OutboxWorker.Message>>any(),
            any(Object[].class)))
        .thenReturn(
            List.of(new OutboxWorker.Message(42, "test@example.invalid", "Subject", "Body", 5)),
            List.of());
    doThrow(new IllegalStateException("Provider failure"))
        .when(gateway)
        .send(anyString(), anyString(), anyString());
    new OutboxWorker(jdbc, gateway).process();
    verify(jdbc)
        .update(
            contains("next_attempt"),
            eq("FAILED"),
            any(Timestamp.class),
            eq("IllegalStateException"),
            eq(42L),
            anyString());
  }
}
