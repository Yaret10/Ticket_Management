package com.yaret.contigo.attachments;

import com.yaret.contigo.shared.AppException;
import java.io.*;
import java.nio.charset.*;
import java.util.*;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Component;

@Component
public class FileValidator {
  public String validate(String name, byte[] data) {
    if (data.length == 0 || data.length > 10 * 1024 * 1024)
      throw new AppException(413, "Adjunte un archivo de entre 1 byte y 10 MB.");
    String extension = name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    try {
      return switch (extension) {
        case "png", "jpg", "jpeg" -> image(extension, data);
        case "pdf" -> {
          if (!starts(data, new byte[] {37, 80, 68, 70, 45})
              || !new String(
                      data,
                      Math.max(0, data.length - 2048),
                      Math.min(2048, data.length),
                      StandardCharsets.ISO_8859_1)
                  .contains("%%EOF")) throw invalid();
          try (var document = org.apache.pdfbox.Loader.loadPDF(data)) {
            if (document.isEncrypted()
                || document.getNumberOfPages() < 1
                || document.getNumberOfPages() > 500) throw invalid();
            document.getDocumentCatalog();
          }
          yield "application/pdf";
        }
        case "txt" -> {
          StandardCharsets.UTF_8
              .newDecoder()
              .onMalformedInput(CodingErrorAction.REPORT)
              .decode(java.nio.ByteBuffer.wrap(data));
          for (byte b : data) if (b == 0 || (b >= 0 && b < 9)) throw invalid();
          yield "text/plain";
        }
        default -> throw new AppException(400, "Formatos permitidos: PDF, PNG, JPG y TXT UTF-8.");
      };
    } catch (IOException e) {
      throw invalid();
    }
  }

  private String image(String extension, byte[] data) throws IOException {
    boolean png = extension.equals("png");
    if (!starts(
        data,
        png
            ? new byte[] {(byte) 137, 80, 78, 71, 13, 10, 26, 10}
            : new byte[] {(byte) 255, (byte) 216, (byte) 255})) throw invalid();
    try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
      var readers = ImageIO.getImageReaders(input);
      if (!readers.hasNext()) throw invalid();
      var reader = readers.next();
      try {
        reader.setInput(input);
        long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
        if (pixels > 25_000_000 || pixels < 1) throw invalid();
        if (reader.read(0) == null) throw invalid();
      } finally {
        reader.dispose();
      }
    }
    return png ? "image/png" : "image/jpeg";
  }

  private boolean starts(byte[] bytes, byte[] prefix) {
    return bytes.length >= prefix.length
        && Arrays.equals(Arrays.copyOf(bytes, prefix.length), prefix);
  }

  private AppException invalid() {
    return new AppException(
        400, "El contenido del archivo no corresponde a su formato o está dañado.");
  }
}
