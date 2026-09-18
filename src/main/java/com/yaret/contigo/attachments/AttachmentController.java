package com.yaret.contigo.attachments;

import java.io.IOException;
import java.util.List;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/tickets/{id}/attachments")
public class AttachmentController {
  private static final java.util.Set<String> PREVIEW_TYPES =
      java.util.Set.of("image/png", "image/jpeg", "application/pdf", "text/plain");
  private final AttachmentService service;

  public AttachmentController(AttachmentService service) {
    this.service = service;
  }

  @GetMapping
  public List<AttachmentService.View> list(@PathVariable Long id) {
    return service.list(id);
  }

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  public AttachmentService.View upload(
      @PathVariable Long id, @RequestPart("file") MultipartFile file) throws IOException {
    return service.upload(id, file);
  }

  @GetMapping("/{attachmentId}")
  public ResponseEntity<Resource> download(@PathVariable Long id, @PathVariable Long attachmentId)
      throws IOException {
    var d = service.download(id, attachmentId);
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_OCTET_STREAM)
        .contentLength(d.size())
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment()
                .filename(d.name(), java.nio.charset.StandardCharsets.UTF_8)
                .build()
                .toString())
        .header("X-Content-Type-Options", "nosniff")
        .header("Content-Security-Policy", "sandbox")
        .header(HttpHeaders.CACHE_CONTROL, "no-store")
        .body(d.resource());
  }

  @GetMapping("/{attachmentId}/view")
  @io.swagger.v3.oas.annotations.Operation(
      summary = "Visualizar evidencia en el navegador",
      description =
          "Requiere acceso al ticket. Admite PNG, JPEG, PDF y TXT UTF-8; devuelve inline, sin caché.")
  public ResponseEntity<Resource> view(
      @PathVariable Long id,
      @PathVariable Long attachmentId,
      jakarta.servlet.http.HttpServletRequest request)
      throws IOException {
    var d = service.download(id, attachmentId);
    if (!PREVIEW_TYPES.contains(d.contentType()))
      throw new com.yaret.contigo.shared.AppException(
          415, "Este formato no admite visualización. Utilice Descargar.");
    MediaType type = MediaType.parseMediaType(d.contentType());
    if (d.contentType().equals("text/plain"))
      type = new MediaType("text", "plain", java.nio.charset.StandardCharsets.UTF_8);
    // Native PDF viewers need objects; web scripts and embedding by other pages remain forbidden.
    String csp =
        "default-src 'none'; script-src 'none'; object-src 'self'; base-uri 'none'; frame-ancestors 'none'";
    if (!d.contentType().equals("application/pdf")) csp += "; sandbox";
    return ResponseEntity.ok()
        .contentType(type)
        .contentLength(d.size())
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.inline()
                .filename(d.name(), java.nio.charset.StandardCharsets.UTF_8)
                .build()
                .toString())
        .header("X-Content-Type-Options", "nosniff")
        .header("Content-Security-Policy", csp)
        .header(HttpHeaders.CACHE_CONTROL, "no-store")
        .body("HEAD".equals(request.getMethod()) ? null : d.resource());
  }
}
