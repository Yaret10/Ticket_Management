package com.yaret.contigo.attachments;

import java.io.IOException;
import org.springframework.core.io.Resource;

public interface FileStorage {
  void store(String key, byte[] bytes) throws IOException;

  Resource load(String key) throws IOException;

  void delete(String key) throws IOException;
}
