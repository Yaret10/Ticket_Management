package com.yaret.contigo.users;

import com.yaret.contigo.areas.Area;
import com.yaret.contigo.areas.AreaRepository;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Operator-only bootstrap, deliberately unavailable through HTTP or normal profiles. */
@Component
@Profile("bootstrap")
public class BootstrapAdmin implements ApplicationRunner {
  private final UserRepository users;
  private final AreaRepository areas;
  private final PasswordEncoder encoder;
  private final PlatformTransactionManager transactions;
  private final ConfigurableApplicationContext context;
  private final String dni, name, email, password;

  public BootstrapAdmin(
      UserRepository users,
      AreaRepository areas,
      PasswordEncoder encoder,
      PlatformTransactionManager transactions,
      ConfigurableApplicationContext context,
      @Value("${BOOTSTRAP_DNI:}") String dni,
      @Value("${BOOTSTRAP_NAME:}") String name,
      @Value("${BOOTSTRAP_EMAIL:}") String email,
      @Value("${BOOTSTRAP_PASSWORD:}") String password) {
    this.users = users;
    this.areas = areas;
    this.encoder = encoder;
    this.transactions = transactions;
    this.context = context;
    this.dni = dni;
    this.name = name;
    this.email = email;
    this.password = password;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (!dni.matches("[0-9]{8}")
        || name.isBlank()
        || name.length() > 120
        || !email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")
        || email.length() > 254
        || password.length() < 12
        || password.getBytes(StandardCharsets.UTF_8).length > 72)
      throw new IllegalStateException("Configure las variables BOOTSTRAP con datos válidos.");
    new TransactionTemplate(transactions)
        .executeWithoutResult(
            status -> {
              if (users.existsByManageUsersTrue())
                throw new IllegalStateException(
                    "Ya existe un administrador; bootstrap no modifica administradores.");
              User user = users.findByDni(dni).orElseGet(User::new);
              if (user.id == null) {
                user.dni = dni;
                user.username = "admin." + java.util.UUID.randomUUID();
                user.name = name;
                user.email = email;
                user.position = "Administrador autorizado";
                user.area =
                    areas.findAll().stream()
                        .filter(a -> a.name.equals("TI"))
                        .findFirst()
                        .orElseGet(() -> areas.save(new Area("TI")));
              }
              user.passwordHash = encoder.encode(password);
              user.enabled = true;
              user.manageUsers = true;
              user.roles.addAll(Set.of(Role.EMPLEADO, Role.TI));
              user.grantableRoles.addAll(Set.of(Role.values()));
              users.saveAndFlush(user);
            });
    org.slf4j.LoggerFactory.getLogger(getClass()).info("administrator_bootstrap_completed");
    SpringApplication.exit(context, () -> 0);
  }
}
