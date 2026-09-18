package com.yaret.contigo;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.yaret.contigo.areas.*;
import com.yaret.contigo.attachments.*;
import com.yaret.contigo.notifications.*;
import com.yaret.contigo.shared.AppException;
import com.yaret.contigo.tickets.*;
import com.yaret.contigo.users.*;
import java.nio.file.Files;
import java.util.*;
import org.junit.jupiter.api.Test;

class DomainTest {
  @Test
  void fileContentAndStoragePathsAreValidated() throws Exception {
    FileValidator v = new FileValidator();
    assertThrows(AppException.class, () -> v.validate("fake.png", "not an image".getBytes()));
    assertThrows(AppException.class, () -> v.validate("script.html", "<script>".getBytes()));
    assertThrows(AppException.class, () -> v.validate("fake.pdf", "%PDF-fake".getBytes()));
    assertEquals(
        "text/plain",
        v.validate(
            "evidence.txt", "Descripción".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    var store = new LocalFileStorage(Files.createTempDirectory("safe-storage-").toString());
    assertThrows(java.io.IOException.class, () -> store.store("../../escape", new byte[] {1}));
    String key = UUID.randomUUID().toString();
    store.store(key, new byte[] {1});
    assertThrows(
        java.nio.file.FileAlreadyExistsException.class, () -> store.store(key, new byte[] {2}));
  }

  @Test
  void pdfContentRequiresAReadableDocument() throws Exception {
    FileValidator validator = new FileValidator();
    assertThrows(
        AppException.class,
        () -> validator.validate("fake.pdf", "%PDF-1.7 invalid %%EOF".getBytes()));
    try (var document = new org.apache.pdfbox.pdmodel.PDDocument();
        var bytes = new java.io.ByteArrayOutputStream()) {
      document.addPage(new org.apache.pdfbox.pdmodel.PDPage());
      document.save(bytes);
      assertEquals("application/pdf", validator.validate("valid.pdf", bytes.toByteArray()));
    }
  }

  @Test
  void allUnspecifiedTransitionsAreDenied() {
    User owner = new User();
    owner.id = 1L;
    owner.roles.addAll(Set.of(Role.values()));
    Area a = new Area("A");
    a.id = 1L;
    owner.area = a;
    owner.managedAreas.add(a);
    Ticket ticket = new Ticket();
    ticket.area = a;
    ticket.requester = owner;
    TicketPolicy p = new TicketPolicy();
    for (TicketState from : TicketState.values())
      for (TicketState to : TicketState.values()) {
        ticket.state = from;
        boolean allowed =
            from == TicketState.PENDIENTE && to == TicketState.APROBADO
                || from == TicketState.APROBADO
                    && (to == TicketState.ATENDIDO || to == TicketState.RECHAZADO)
                || from == TicketState.ATENDIDO && to == TicketState.CERRADO;
        if (allowed) assertDoesNotThrow(() -> p.transition(owner, ticket, to, "Informe"));
        else assertThrows(AppException.class, () -> p.transition(owner, ticket, to, "Informe"));
      }
  }
}
