package com.yaret.contigo.tickets;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/tickets")
public class TicketController {
  private final TicketService service;

  public TicketController(TicketService service) {
    this.service = service;
  }

  @PostMapping
  @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
  public TicketView create(@Valid @RequestBody TicketService.Create d) {
    return service.create(d);
  }

  @GetMapping
  public TicketService.Results list(
      @RequestParam(required = false) String q,
      @RequestParam(required = false) TicketState state,
      @RequestParam(required = false) Priority priority,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "createdAt") String sort,
      @RequestParam(defaultValue = "false") boolean ascending) {
    return service.list(new TicketQuery(q, state, priority, page, size, sort, ascending));
  }

  @GetMapping("/{id}")
  public TicketView detail(@PathVariable Long id) {
    return service.detail(id);
  }

  @GetMapping("/{id}/history")
  public List<TicketService.History> history(@PathVariable Long id) {
    return service.history(id);
  }

  @PostMapping("/{id}/approve")
  public TicketView approve(@PathVariable Long id, @Valid @RequestBody TicketService.Change d) {
    return service.transition(id, TicketState.APROBADO, d);
  }

  @PostMapping("/{id}/attend")
  public TicketView attend(@PathVariable Long id, @Valid @RequestBody TicketService.Change d) {
    return service.transition(id, TicketState.ATENDIDO, d);
  }

  @PostMapping("/{id}/reject")
  public TicketView reject(@PathVariable Long id, @Valid @RequestBody TicketService.Change d) {
    return service.transition(id, TicketState.RECHAZADO, d);
  }

  @PostMapping("/{id}/close")
  public TicketView close(@PathVariable Long id, @Valid @RequestBody TicketService.Change d) {
    return service.transition(id, TicketState.CERRADO, d);
  }
}
