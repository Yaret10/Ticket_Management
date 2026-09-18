package com.yaret.contigo.tickets;

import java.time.Instant;

public record TicketView(
    Long id,
    long version,
    String code,
    String equipment,
    String pcUser,
    String description,
    Priority priority,
    TicketState state,
    Long requesterId,
    String requester,
    String area,
    Long areaId,
    String approver,
    String technician,
    String technicalReport,
    Instant createdAt,
    Instant attendedAt,
    Instant closedAt) {
  public static TicketView of(Ticket t) {
    return new TicketView(
        t.id,
        t.version,
        t.code,
        t.equipment,
        t.pcUser,
        t.description,
        t.priority,
        t.state,
        t.requester.getId(),
        t.requester.getName(),
        t.area.getName(),
        t.area.getId(),
        t.approver == null ? null : t.approver.getName(),
        t.technician == null ? null : t.technician.getName(),
        t.technicalReport,
        t.createdAt,
        t.attendedAt,
        t.closedAt);
  }
}
