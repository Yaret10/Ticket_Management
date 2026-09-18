package com.yaret.contigo.tickets;

import com.yaret.contigo.areas.Area;
import com.yaret.contigo.users.User;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "tickets")
public class Ticket {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Version public long version;

  @Column(nullable = false, unique = true, length = 40)
  public String code;

  @org.hibernate.annotations.Nationalized
  @Column(nullable = false, length = 120)
  public String equipment;

  @org.hibernate.annotations.Nationalized
  @Column(nullable = false, length = 100)
  public String pcUser;

  @org.hibernate.annotations.Nationalized
  @Column(nullable = false, length = 4000)
  public String description;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  public Priority priority;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  public TicketState state;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "requester_id")
  public User requester;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "area_id")
  public Area area;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "approver_id")
  public User approver;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "technician_id")
  public User technician;

  @org.hibernate.annotations.Nationalized
  @Column(length = 4000)
  public String technicalReport;

  @Column(nullable = false)
  public Instant createdAt;

  public Instant attendedAt;
  public Instant closedAt;

  public Long getId() {
    return id;
  }
}
