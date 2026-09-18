package com.yaret.contigo.tickets;

import com.yaret.contigo.shared.AppException;
import java.util.Set;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;

public record TicketQuery(
    String q,
    TicketState state,
    Priority priority,
    int page,
    int size,
    String sort,
    boolean ascending) {
  public Pageable pageable() {
    if (page < 0 || page > 100000 || size < 1 || size > 100 || q != null && q.length() > 200)
      throw new AppException(400, "Paginación o búsqueda fuera de los límites permitidos.");
    if (!Set.of("createdAt", "code", "priority", "state").contains(sort))
      throw new AppException(400, "Campo de ordenamiento no permitido.");
    return PageRequest.of(
        page,
        size,
        Sort.by(ascending ? Sort.Direction.ASC : Sort.Direction.DESC, sort).and(Sort.by("id")));
  }

  public Specification<Ticket> filter() {
    return (r, z, c) -> {
      var p = c.conjunction();
      if (state != null) p = c.and(p, c.equal(r.get("state"), state));
      if (priority != null) p = c.and(p, c.equal(r.get("priority"), priority));
      if (q != null && !q.isBlank()) {
        String value =
            "%"
                + q.toLowerCase(java.util.Locale.ROOT)
                    .replace("!", "!!")
                    .replace("%", "!%")
                    .replace("_", "!_")
                + "%";
        p =
            c.and(
                p,
                c.or(
                    c.like(c.lower(r.get("code")), value, '!'),
                    c.like(c.lower(r.get("equipment")), value, '!'),
                    c.like(c.lower(r.get("description")), value, '!')));
      }
      return p;
    };
  }
}
