package com.yaret.contigo.auth;

import com.yaret.contigo.users.User;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "refresh_families")
public class RefreshFamily {
  @Id
  @Column(length = 36)
  public String id;

  @Version public long version;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id")
  public User user;

  @Column(nullable = false)
  public Instant createdAt;

  @Column(nullable = false)
  public Instant expiresAt;

  public boolean revoked;
}
