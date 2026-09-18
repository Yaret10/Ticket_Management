package com.yaret.contigo.auth;

import com.yaret.contigo.users.User;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(nullable = false, unique = true, length = 64)
  public String tokenHash;

  @Column(nullable = false, length = 36)
  public String family;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "family", insertable = false, updatable = false)
  public RefreshFamily familyState;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id")
  public User user;

  @Column(nullable = false)
  public Instant expiresAt;

  public Instant usedAt;
  public boolean revoked;
  @Version public long version;
}
