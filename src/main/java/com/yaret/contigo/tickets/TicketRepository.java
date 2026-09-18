package com.yaret.contigo.tickets;

import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.*;

public interface TicketRepository
    extends JpaRepository<Ticket, Long>, JpaSpecificationExecutor<Ticket> {
  @Override
  @EntityGraph(attributePaths = {"requester", "area", "approver", "technician"})
  Page<Ticket> findAll(Specification<Ticket> spec, Pageable pageable);

  @Override
  @EntityGraph(attributePaths = {"requester", "area", "approver", "technician"})
  Optional<Ticket> findById(Long id);
}
