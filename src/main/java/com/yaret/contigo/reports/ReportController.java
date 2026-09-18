package com.yaret.contigo.reports;

import com.yaret.contigo.tickets.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {
  private final ReportService reports;
  private final ExcelExporter excel;

  public ReportController(ReportService reports, ExcelExporter excel) {
    this.reports = reports;
    this.excel = excel;
  }

  @GetMapping("/dashboard")
  public ReportService.Dashboard dashboard() {
    return reports.dashboard();
  }

  @GetMapping("/excel")
  public ResponseEntity<StreamingResponseBody> excel(
      @RequestParam(required = false) String q,
      @RequestParam(required = false) TicketState state,
      @RequestParam(required = false) Priority priority) {
    // Authorization is captured on the request thread before Spring starts asynchronous streaming.
    var scope = reports.scope(new TicketQuery(q, state, priority, 0, 100, "createdAt", false));
    long ceiling = reports.ceiling();
    return ResponseEntity.ok()
        .contentType(
            MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=tickets.xlsx")
        .body(output -> excel.write(scope, ceiling, output));
  }
}
