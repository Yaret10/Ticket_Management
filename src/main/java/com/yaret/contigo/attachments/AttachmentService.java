package com.yaret.contigo.attachments;

import com.yaret.contigo.shared.AppException;
import com.yaret.contigo.tickets.*;
import com.yaret.contigo.users.*;
import jakarta.persistence.*;
import java.io.*;
import java.time.Instant;
import java.util.*;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AttachmentService {
  public record View(Long id, String name, String contentType, long size, Instant createdAt) {
    static View of(Attachment a) {
      return new View(a.id, a.originalName, a.contentType, a.size, a.createdAt);
    }
  }

  public record Download(String name, String contentType, long size, Resource resource) {}

  private final AttachmentRepository attachments;
  private final TicketService tickets;
  private final CurrentUser current;
  private final TicketPolicy policy;
  private final FileStorage storage;
  private final FileValidator validator;
  private final EntityManager em;

  public AttachmentService(
      AttachmentRepository attachments,
      TicketService tickets,
      CurrentUser current,
      TicketPolicy policy,
      FileStorage storage,
      FileValidator validator,
      EntityManager em) {
    this.attachments = attachments;
    this.tickets = tickets;
    this.current = current;
    this.policy = policy;
    this.storage = storage;
    this.validator = validator;
    this.em = em;
  }

  @Transactional
  public View upload(Long id, MultipartFile file) throws IOException {
    Ticket t = tickets.find(id);
    User actor = current.get();
    policy.attach(actor, t);
    // Force a version increment: an approval racing this upload cannot commit against the same
    // version.
    em.lock(t, LockModeType.OPTIMISTIC_FORCE_INCREMENT);
    String original = Optional.ofNullable(file.getOriginalFilename()).orElse("evidencia");
    original = original.replace('\\', '/');
    original =
        original.substring(original.lastIndexOf('/') + 1).replaceAll("[^\\p{L}\\p{N}._ -]", "_");
    if (original.length() > 150 || original.isBlank())
      throw new AppException(400, "El nombre de archivo no es válido.");
    byte[] data;
    try (var input = file.getInputStream()) {
      data = input.readNBytes(10 * 1024 * 1024 + 1);
    }
    String mime = validator.validate(original, data);
    String key = UUID.randomUUID().toString();
    storage.store(key, data);
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCompletion(int status) {
            if (status != STATUS_COMMITTED)
              try {
                storage.delete(key);
              } catch (IOException ignored) {
                org.slf4j.LoggerFactory.getLogger(getClass())
                    .warn("attachment_cleanup_required storageKey={}", key);
              }
          }
        });
    Attachment a = new Attachment();
    a.ticket = t;
    a.uploader = actor;
    a.storageKey = key;
    a.originalName = original;
    a.contentType = mime;
    a.size = data.length;
    a.sha256 = sha(data);
    a.createdAt = Instant.now();
    return View.of(attachments.saveAndFlush(a));
  }

  @Transactional(readOnly = true)
  public List<View> list(Long id) {
    Ticket t = tickets.find(id);
    policy.read(current.get(), t);
    return attachments.findByTicketIdOrderByIdAsc(id).stream().map(View::of).toList();
  }

  @Transactional(readOnly = true)
  public Download download(Long id, Long attachmentId) throws IOException {
    Ticket t = tickets.find(id);
    policy.read(current.get(), t);
    Attachment a = attachments.findById(attachmentId).orElseThrow(AppException::missing);
    if (!a.ticket.getId().equals(id)) throw AppException.missing();
    Resource resource = storage.load(a.storageKey);
    if (!resource.exists()) throw AppException.missing();
    return new Download(a.originalName, a.contentType, a.size, resource);
  }

  private String sha(byte[] data) {
    try {
      return HexFormat.of()
          .formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(data));
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
