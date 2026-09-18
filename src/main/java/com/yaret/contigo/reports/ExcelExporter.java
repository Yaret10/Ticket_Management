package com.yaret.contigo.reports;

import java.io.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Component;

@Component
public class ExcelExporter {
  private final ReportService reports;

  public ExcelExporter(ReportService reports) {
    this.reports = reports;
  }

  public void write(ReportService.Scope scope, long ceiling, OutputStream output)
      throws IOException {
    try (var workbook = new SXSSFWorkbook(100)) {
      workbook.setCompressTempFiles(true);
      Sheet sheet = null;
      int row = 0, sheetNumber = 0;
      long cursor = 0;
      do {
        var batch = reports.batch(scope, cursor, ceiling);
        if (batch.isEmpty()) break;
        for (var t : batch) {
          if (sheet == null || row >= 1_048_576) {
            sheet = workbook.createSheet("Tickets " + (++sheetNumber));
            row = 0;
            cells(
                sheet.createRow(row++),
                new String[] {
                  "Código",
                  "Equipo",
                  "Usuario PC",
                  "Solicitante",
                  "Área",
                  "Prioridad",
                  "Estado",
                  "Descripción",
                  "Informe técnico",
                  "Registro (Lima)",
                  "Atención (Lima)",
                  "Cierre (Lima)"
                });
          }
          cells(
              sheet.createRow(row++),
              new String[] {
                t.code(),
                t.equipment(),
                t.pcUser(),
                t.requester(),
                t.area(),
                t.priority().name(),
                t.state().name(),
                t.description(),
                t.technicalReport(),
                date(t.createdAt()),
                date(t.attendedAt()),
                date(t.closedAt())
              });
        }
        cursor = batch.getLast().id();
      } while (true);
      if (sheet == null) {
        sheet = workbook.createSheet("Tickets");
        sheet.createRow(0).createCell(0).setCellValue("No se encontraron resultados.");
      }
      workbook.write(output);
    }
  }

  private void cells(Row row, String[] values) {
    for (int i = 0; i < values.length; i++) {
      Cell cell = row.createCell(i, CellType.STRING);
      cell.setCellValue(values[i] == null ? "" : values[i]);
    }
  }

  private String date(Instant date) {
    return date == null
        ? ""
        : DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
            .withZone(ZoneId.of("America/Lima"))
            .format(date);
  }
}
