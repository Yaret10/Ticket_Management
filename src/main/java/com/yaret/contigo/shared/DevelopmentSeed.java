package com.yaret.contigo.shared;

import com.yaret.contigo.areas.*;
import com.yaret.contigo.users.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("local")
@ConditionalOnProperty(name = "app.dev.seed", havingValue = "true")
public class DevelopmentSeed implements ApplicationRunner {
  private final UserRepository users;
  private final AreaRepository areas;
  private final PasswordEncoder encoder;
  private final String password;
  private final JdbcTemplate jdbc;

  public DevelopmentSeed(
      UserRepository users,
      AreaRepository areas,
      PasswordEncoder encoder,
      @Value("${DEV_PASSWORD:}") String password,
      JdbcTemplate jdbc) {
    this.users = users;
    this.areas = areas;
    this.encoder = encoder;
    this.password = password;
    this.jdbc = jdbc;
  }

  @Transactional
  public void run(ApplicationArguments args) {
    if (users.count() != 0) return;
    if (password.length() < 12 || password.length() > 72)
      throw new IllegalStateException("DEV_PASSWORD requiere de 12 a 72 caracteres.");
    Area operations = areas.save(new Area("Operaciones"));
    Area finance = areas.save(new Area("Administración"));
    Area ti = areas.save(new Area("TI"));
    add(
        "00000001",
        "solicitante.demo",
        "Solicitante de prueba",
        operations,
        Set.of(Role.EMPLEADO),
        Set.of(),
        false);
    add(
        "00000002",
        "jefe.demo",
        "Jefe de prueba",
        operations,
        Set.of(Role.EMPLEADO, Role.JEFE),
        Set.of(),
        false);
    add("00000003", "ti.demo", "TI de prueba", ti, Set.of(Role.EMPLEADO, Role.TI), Set.of(), true);
    add(
        "00000004",
        "otro.demo",
        "Otro solicitante",
        finance,
        Set.of(Role.EMPLEADO),
        Set.of(),
        false);
    add(
        "00000005",
        "gerente.demo",
        "Gerente de prueba",
        operations,
        Set.of(Role.EMPLEADO, Role.GERENTE),
        Set.of(operations, finance),
        false);
    jdbc.update(
        "INSERT INTO area_notification_recipients(area_id,email) VALUES (?,?)",
        operations.id,
        "jefe.demo@example.invalid");
    jdbc.update(
        "INSERT INTO area_notification_recipients(area_id,email) VALUES (?,?)",
        finance.id,
        "gerente.demo@example.invalid");
  }

  private void add(
      String dni,
      String username,
      String name,
      Area area,
      Set<Role> roles,
      Set<Area> managed,
      boolean admin) {
    User u = new User();
    u.dni = dni;
    u.username = username;
    u.name = name;
    u.email = username + "@example.invalid";
    u.passwordHash = encoder.encode(password);
    u.position = "Personal de prueba";
    u.area = area;
    u.roles.addAll(roles);
    u.managedAreas.addAll(managed);
    u.manageUsers = admin;
    if (admin) u.grantableRoles.addAll(Set.of(Role.values()));
    users.saveAndFlush(u);
  }
}
