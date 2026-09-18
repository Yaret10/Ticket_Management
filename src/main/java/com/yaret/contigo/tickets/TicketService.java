package com.yaret.contigo.tickets;

import com.yaret.contigo.notifications.NotificationService;
import com.yaret.contigo.shared.AppException;
import com.yaret.contigo.users.*;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TicketService {
  public record Create(
      @NotBlank @Size(max = 120) String equipment,
      @NotBlank @Size(max = 100) String pcUser,
      @NotBlank @Size(max = 4000) String description,
      @NotNull Priority priority) {}

  public record Change(
      @NotNull @PositiveOrZero Long version, @Size(max = 4000) String observation) {}

  public record History(
      Long id,
      String actor,
      Instant date,
      TicketState previousState,
      TicketState newState,
      String observation) {}

  public record Results(
      List<TicketView> content, long totalElements, int totalPages, int number, int size) {}

  private final TicketRepository tickets;
  private final HistoryRepository history;
  private final CurrentUser current;
  private final TicketPolicy policy;
  private final JdbcTemplate jdbc;
  private final NotificationService notifications;
  private final EntityManager em;

  public TicketService(
      TicketRepository tickets,
      HistoryRepository history,
      CurrentUser current,
      TicketPolicy policy,
      JdbcTemplate jdbc,
      NotificationService notifications,
      EntityManager em) {
    this.tickets = tickets;
    this.history = history;
    this.current = current;
    this.policy = policy;
    this.jdbc = jdbc;
    this.notifications = notifications;
    this.em = em;
  }

  @Transactional
  public TicketView create(Create d) {
    User actor = current.get();
    if (!actor.has(Role.EMPLEADO)) throw AppException.forbidden();
    Ticket t = new Ticket();
    t.createdAt = Instant.now();
    Long serial = jdbc.queryForObject("SELECT NEXT VALUE FOR ticket_code_seq", Long.class);
    t.code =
        "TCK-"
            + java.time.format.DateTimeFormatter.ofPattern("yyyyMM")
                .withZone(ZoneId.of("America/Lima"))
                .format(t.createdAt)
            + "-"
            + String.format(java.util.Locale.ROOT, "%04d", serial);
    t.equipment = d.equipment();
    t.pcUser = d.pcUser();
    t.description = d.description();
    t.priority = d.priority();
    t.state = TicketState.PENDIENTE;
    t.requester = actor;
    t.area = actor.area;
    tickets.saveAndFlush(t);
    record(t, actor, null, "Ticket registrado.");
    notifications.enqueue(t, true);
    return TicketView.of(t);
  }

  @Transactional(readOnly = true)
  public Results list(TicketQuery query) {
    var page =
        tickets.findAll(policy.visibility(current.get()).and(query.filter()), query.pageable());
    return new Results(
        page.map(TicketView::of).getContent(),
        page.getTotalElements(),
        page.getTotalPages(),
        page.getNumber(),
        page.getSize());
  }

  @Transactional(readOnly = true)
  public TicketView detail(Long id) {
    Ticket t = find(id);
    policy.read(current.get(), t);
    return TicketView.of(t);
  }

  @Transactional(readOnly = true)
  public List<History> history(Long id) {
    Ticket t = find(id);
    policy.read(current.get(), t);
    return history.findByTicketIdOrderByIdAsc(id).stream()
        .map(
            h ->
                new History(
                    h.id,
                    h.actor.getName(),
                    h.changedAt,
                    h.previousState,
                    h.newState,
                    h.observation))
        .toList();
  }

  @Transactional
  public TicketView transition(Long id, TicketState next, Change d) {
    Ticket t = find(id);
    User actor = current.get();
    policy.transition(actor, t, next, d.observation());
    if (t.version != d.version())
      throw new AppException(409, "El ticket fue modificado. Actualice sus datos.");
    TicketState previous = t.state;
    t.state = next;
    switch (next) {
      case APROBADO -> t.approver = actor;
      case ATENDIDO, RECHAZADO -> {
        t.technician = actor;
        t.technicalReport = d.observation();
        t.attendedAt = Instant.now();
      }
      case CERRADO -> t.closedAt = Instant.now();
      default -> {}
    }
    tickets.saveAndFlush(t);
    record(t, actor, previous, d.observation());
    notifications.enqueue(t, false);
    return TicketView.of(t);
  }

  public Ticket find(Long id) {
    return tickets.findById(id).orElseThrow(AppException::missing);
  }

  private void record(Ticket t, User actor, TicketState previous, String observation) {
    TicketHistory h = new TicketHistory();
    h.ticket = t;
    h.actor = actor;
    h.previousState = previous;
    h.newState = t.state;
    h.changedAt = Instant.now();
    h.observation = observation;
    history.save(h);
  }
}
