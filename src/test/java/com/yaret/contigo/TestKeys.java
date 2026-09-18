package com.yaret.contigo;

import java.nio.file.*;
import java.security.*;
import java.util.Base64;
import org.springframework.test.context.DynamicPropertyRegistry;

public final class TestKeys {
  private static Path directory;

  public static synchronized void configure(DynamicPropertyRegistry registry) {
    try {
      if (directory == null) {
        directory = Files.createTempDirectory("contigo-test-");
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var pair = generator.generateKeyPair();
        pem("private.pem", "PRIVATE KEY", pair.getPrivate().getEncoded());
        pem("public.pem", "PUBLIC KEY", pair.getPublic().getEncoded());
      }
      registry.add(
          "app.auth.private-key", () -> directory.resolve("private.pem").toUri().toString());
      registry.add("app.auth.public-key", () -> directory.resolve("public.pem").toUri().toString());
      registry.add("app.storage.root", () -> directory.resolve("evidence").toString());
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private static void pem(String name, String type, byte[] bytes) throws Exception {
    Files.writeString(
        directory.resolve(name),
        "-----BEGIN "
            + type
            + "-----\n"
            + Base64.getMimeEncoder(64, new byte[] {10}).encodeToString(bytes)
            + "\n-----END "
            + type
            + "-----\n");
  }
}
