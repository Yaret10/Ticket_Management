package com.yaret.contigo.tickets;

import com.yaret.contigo.users.User;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "ticket_history")
public class TicketHistory {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "ticket_id")
  public Ticket ticket;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "actor_id")
  public User actor;

  @Enumerated(EnumType.STRING)
  @Column(length = 20)
  public TicketState previousState;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  public TicketState newState;

  @Column(nullable = false)
  public Instant changedAt;

  @org.hibernate.annotations.Nationalized
  @Column(length = 4000)
  public String observation;
}
