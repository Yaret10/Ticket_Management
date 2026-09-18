package com.yaret.contigo.tickets;

import java.util.List;
import org.springframework.data.jpa.repository.*;

public interface HistoryRepository extends JpaRepository<TicketHistory, Long> {
  @EntityGraph(attributePaths = "actor")
  List<TicketHistory> findByTicketIdOrderByIdAsc(Long id);
}
