package com.yaret.contigo.notifications;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "notification_outbox")
public class OutboxMessage {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @org.hibernate.annotations.Nationalized
  @Column(nullable = false, length = 254)
  public String recipient;

  @org.hibernate.annotations.Nationalized
  @Column(nullable = false, length = 200)
  public String subject;

  @org.hibernate.annotations.Nationalized
  @Column(nullable = false, length = 4000)
  public String body;

  @Column(nullable = false, length = 20)
  public String status = "PENDING";

  public int attempts;

  @Column(nullable = false)
  public Instant nextAttempt = Instant.now();

  public Instant leaseUntil;

  @Column(length = 36)
  public String leaseOwner;

  @org.hibernate.annotations.Nationalized
  @Column(length = 200)
  public String lastError;

  @Column(nullable = false)
  public Instant createdAt = Instant.now();

  public Instant sentAt;
}
