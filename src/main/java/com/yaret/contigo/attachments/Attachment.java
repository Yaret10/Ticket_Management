package com.yaret.contigo.attachments;

import com.yaret.contigo.tickets.Ticket;
import com.yaret.contigo.users.User;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "attachments")
public class Attachment {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "ticket_id")
  public Ticket ticket;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "uploaded_by")
  public User uploader;

  @Column(nullable = false, unique = true, length = 36)
  public String storageKey;

  @org.hibernate.annotations.Nationalized
  @Column(nullable = false, length = 150)
  public String originalName;

  @Column(nullable = false, length = 100)
  public String contentType;

  @Column(nullable = false)
  public long size;

  @Column(nullable = false, length = 64)
  public String sha256;

  @Column(nullable = false)
  public Instant createdAt;
}
