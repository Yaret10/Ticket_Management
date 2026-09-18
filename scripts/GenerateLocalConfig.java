import java.nio.file.*;
import java.security.*;
import java.util.*;

public class GenerateLocalConfig {
  public static void main(String[] args) throws Exception {
    Path root = Path.of(".local");
    Files.createDirectories(root);
    Path privatePath = root.resolve("private.pem"), publicPath = root.resolve("public.pem");
    if (!Files.exists(privatePath) && !Files.exists(publicPath)) {
      KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(3072);
      KeyPair pair = generator.generateKeyPair();
      pem(privatePath, "PRIVATE KEY", pair.getPrivate().getEncoded());
      pem(publicPath, "PUBLIC KEY", pair.getPublic().getEncoded());
    } else if (!Files.exists(privatePath) || !Files.exists(publicPath))
      throw new IllegalStateException("Falta una de las claves; no se sobrescriben archivos.");
    Path env = Path.of(".env");
    if (!Files.exists(env)) {
      String db = random(), dev = random();
      Files.writeString(
          env,
          "DB_PASSWORD="
              + db
              + "\nDEV_PASSWORD="
              + dev
              + "\nDEV_SEED=true\nSPRING_PROFILES_ACTIVE=local\nMAIL_MODE=local\n",
          StandardOpenOption.CREATE_NEW);
    }
    System.out.println(
        "Configuración local preparada. Consulte DEV_PASSWORD en .env; no se imprime ningún secreto.");
  }

  private static String random() {
    byte[] bytes = new byte[24];
    new SecureRandom().nextBytes(bytes);
    return "Aa1!" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private static void pem(Path path, String label, byte[] bytes) throws Exception {
    String value =
        "-----BEGIN "
            + label
            + "-----\n"
            + Base64.getMimeEncoder(64, new byte[] {10}).encodeToString(bytes)
            + "\n-----END "
            + label
            + "-----\n";
    Files.writeString(path, value, StandardOpenOption.CREATE_NEW);
  }
}
