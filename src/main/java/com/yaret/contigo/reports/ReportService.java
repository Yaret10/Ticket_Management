package com.yaret.contigo.reports;

import com.yaret.contigo.tickets.*;
import com.yaret.contigo.users.*;
import jakarta.persistence.*;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReportService {
  private final TicketRepository tickets;
  private final CurrentUser current;
  private final TicketPolicy policy;
  private final EntityManager em;

  public ReportService(
      TicketRepository tickets, CurrentUser current, TicketPolicy policy, EntityManager em) {
    this.tickets = tickets;
    this.current = current;
    this.policy = policy;
    this.em = em;
  }

  public record Dashboard(
      long total,
      Map<String, Long> byState,
      Map<String, Long> byPriority,
      List<MonthCount> byMonth,
      double averageLeadDays) {}

  public record MonthCount(String month, long count) {}

  @Transactional(readOnly = true)
  public Dashboard dashboard() {
    var visibility = policy.visibility(current.get());
    Map<String, Long> states = group(visibility, "state"),
        priorities = group(visibility, "priority");
    // Group dates in Lima in SQL Server; UTC timestamps stay unchanged in storage.
    var cb = em.getCriteriaBuilder();
    var query = cb.createQuery(Object[].class);
    var root = query.from(Ticket.class);
    var lima = cb.function("contigo_lima", java.time.Instant.class, root.get("createdAt"));
    var year = cb.function("year", Integer.class, lima);
    var month = cb.function("month", Integer.class, lima);
    query
        .multiselect(year, month, cb.count(root))
        .where(visibility.toPredicate(root, query, cb))
        .groupBy(year, month)
        .orderBy(cb.asc(year), cb.asc(month));
    var months =
        em.createQuery(query).getResultList().stream()
            .map(
                a ->
                    new MonthCount(
                        String.format(java.util.Locale.ROOT, "%04d-%02d", a[0], a[1]),
                        ((Number) a[2]).longValue()))
            .toList();
    return new Dashboard(
        states.values().stream().mapToLong(Long::longValue).sum(),
        states,
        priorities,
        months,
        average(visibility));
  }

  private Map<String, Long> group(Specification<Ticket> visibility, String field) {
    var cb = em.getCriteriaBuilder();
    var query = cb.createQuery(Object[].class);
    var root = query.from(Ticket.class);
    query
        .multiselect(root.get(field), cb.count(root))
        .where(visibility.toPredicate(root, query, cb))
        .groupBy(root.get(field));
    Map<String, Long> result = new LinkedHashMap<>();
    em.createQuery(query)
        .getResultList()
        .forEach(a -> result.put(a[0].toString(), ((Number) a[1]).longValue()));
    return result;
  }

  private double average(Specification<Ticket> visibility) {
    // Keep result sets bounded while summing elapsed time through keyset batches.
    long cursor = 0, count = 0;
    double days = 0;
    while (true) {
      final long lower = cursor;
      var cb = em.getCriteriaBuilder();
      var query = cb.createQuery(Object[].class);
      var root = query.from(Ticket.class);
      query
          .multiselect(root.get("id"), root.get("createdAt"), root.get("attendedAt"))
          .where(
              cb.and(
                  visibility.toPredicate(root, query, cb),
                  cb.greaterThan(root.get("id"), lower),
                  cb.isNotNull(root.get("attendedAt"))))
          .orderBy(cb.asc(root.get("id")));
      var batch = em.createQuery(query).setMaxResults(500).getResultList();
      if (batch.isEmpty()) break;
      for (Object[] t : batch) {
        days +=
            java.time.Duration.between((java.time.Instant) t[1], (java.time.Instant) t[2])
                    .toSeconds()
                / 86400.0;
        count++;
      }
      cursor = ((Number) batch.getLast()[0]).longValue();
    }
    return count == 0 ? 0 : Math.round(days / count * 10) / 10.0;
  }

  @Transactional(readOnly = true)
  public Scope scope(TicketQuery query) {
    query.pageable();
    User u = current.get();
    return new Scope(u.id, u.has(Role.TI), policy.areas(u), query);
  }

  public record Scope(Long userId, boolean ti, Set<Long> areas, TicketQuery query) {}

  @Transactional(readOnly = true)
  public List<TicketView> batch(Scope scope, long after, long ceiling) {
    Specification<Ticket> visibility = policy.visibility(scope.userId(), scope.ti(), scope.areas());
    var cb = em.getCriteriaBuilder();
    var query = cb.createQuery(Ticket.class);
    var root = query.from(Ticket.class);
    root.fetch("requester", jakarta.persistence.criteria.JoinType.INNER);
    root.fetch("area", jakarta.persistence.criteria.JoinType.INNER);
    root.fetch("approver", jakarta.persistence.criteria.JoinType.LEFT);
    root.fetch("technician", jakarta.persistence.criteria.JoinType.LEFT);
    var filters = visibility.and(scope.query().filter());
    query
        .select(root)
        .where(
            cb.and(
                filters.toPredicate(root, query, cb),
                cb.greaterThan(root.get("id"), after),
                cb.lessThanOrEqualTo(root.get("id"), ceiling)))
        .orderBy(cb.asc(root.get("id")));
    return em.createQuery(query).setMaxResults(500).getResultList().stream()
        .map(TicketView::of)
        .toList();
  }

  @Transactional(readOnly = true)
  public long ceiling() {
    return Optional.ofNullable(
            em.createQuery("select max(t.id) from Ticket t", Long.class).getSingleResult())
        .orElse(0L);
  }
}
