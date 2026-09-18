package com.yaret.contigo.attachments;

import java.io.IOException;
import java.nio.file.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.*;
import org.springframework.stereotype.Component;

@Component
public class LocalFileStorage implements FileStorage {
  private final Path root;

  public LocalFileStorage(@Value("${app.storage.root}") String root) throws IOException {
    this.root = Path.of(root).toAbsolutePath().normalize();
    Files.createDirectories(this.root);
  }

  private Path path(String key) throws IOException {
    if (!key.matches("[a-f0-9-]{36}")) throw new IOException("Invalid storage key");
    Path path = root.resolve(key).normalize();
    if (!path.getParent().equals(root) || Files.isSymbolicLink(path))
      throw new IOException("Unsafe storage path");
    return path;
  }

  public void store(String key, byte[] bytes) throws IOException {
    Files.write(path(key), bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
  }

  public Resource load(String key) throws IOException {
    return new FileSystemResource(path(key));
  }

  public void delete(String key) throws IOException {
    Files.deleteIfExists(path(key));
  }
}
