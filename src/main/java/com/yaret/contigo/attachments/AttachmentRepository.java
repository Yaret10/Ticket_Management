package com.yaret.contigo.attachments;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttachmentRepository extends JpaRepository<Attachment, Long> {
  List<Attachment> findByTicketIdOrderByIdAsc(Long id);
}
