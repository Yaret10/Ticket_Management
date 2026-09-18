package com.yaret.contigo.tickets;

import com.yaret.contigo.shared.AppException;
import com.yaret.contigo.users.*;
import java.util.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

@Component
public class TicketPolicy {
  public Set<Long> areas(User u) {
    Set<Long> authorized = new HashSet<>();
    if (u.has(Role.JEFE)) authorized.add(u.area.getId());
    if (u.has(Role.GERENTE)) u.managedAreas.forEach(a -> authorized.add(a.getId()));
    return Set.copyOf(authorized);
  }

  public boolean visible(User u, Ticket t) {
    return u.has(Role.TI)
        || Objects.equals(t.requester.getId(), u.id)
        || areas(u).contains(t.area.getId());
  }

  public void read(User u, Ticket t) {
    if (!visible(u, t)) throw AppException.forbidden();
  }

  public Specification<Ticket> visibility(User u) {
    return visibility(u.getId(), u.has(Role.TI), areas(u));
  }

  public Specification<Ticket> visibility(Long userId, boolean ti, Set<Long> authorizedAreas) {
    Set<Long> areas = Set.copyOf(authorizedAreas);
    return (r, q, c) ->
        ti
            ? c.conjunction()
            : c.or(
                c.equal(r.get("requester").get("id"), userId),
                areas.isEmpty() ? c.disjunction() : r.get("area").get("id").in(areas));
  }

  public void transition(User u, Ticket t, TicketState next, String observation) {
    read(u, t);
    boolean permission =
        switch (next) {
          case APROBADO -> areas(u).contains(t.area.getId());
          case ATENDIDO, RECHAZADO -> u.has(Role.TI);
          case CERRADO -> Objects.equals(u.id, t.requester.getId());
          default -> false;
        };
    if (!permission) throw AppException.forbidden();
    TicketState required =
        switch (next) {
          case APROBADO -> TicketState.PENDIENTE;
          case ATENDIDO, RECHAZADO -> TicketState.APROBADO;
          case CERRADO -> TicketState.ATENDIDO;
          default -> null;
        };
    if (t.state != required)
      throw new AppException(409, "Esta transición no es válida para el estado actual del ticket.");
    if ((next == TicketState.ATENDIDO || next == TicketState.RECHAZADO)
        && (observation == null || observation.isBlank()))
      throw new AppException(400, "Indique el informe técnico o motivo de rechazo.");
  }

  public void attach(User u, Ticket t) {
    if (!Objects.equals(u.id, t.requester.getId())) throw AppException.forbidden();
    if (t.state != TicketState.PENDIENTE)
      throw new AppException(
          409, "Solo puede adjuntar evidencias mientras el ticket está PENDIENTE.");
  }
}
