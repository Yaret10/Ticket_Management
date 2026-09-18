package com.yaret.contigo;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.yaret.contigo.areas.*;
import com.yaret.contigo.auth.*;
import com.yaret.contigo.notifications.*;
import com.yaret.contigo.tickets.*;
import com.yaret.contigo.users.*;
import jakarta.persistence.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.mssqlserver.MSSQLServerContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class SqlServerIT {
  @Container
  static MSSQLServerContainer db =
      new MSSQLServerContainer(
              DockerImageName.parse("mcr.microsoft.com/mssql/server:2022-CU26-ubuntu-22.04"))
          .acceptLicense();

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry r) {
    TestKeys.configure(r);
    r.add("spring.datasource.url", db::getJdbcUrl);
    r.add("spring.datasource.username", db::getUsername);
    r.add("spring.datasource.password", db::getPassword);
    r.add("spring.flyway.enabled", () -> "true");
    r.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
  }

  @MockitoBean OutboxWorker scheduledWorker;
  @Autowired JdbcTemplate jdbc;
  @Autowired LoginGuard guard;
  @Autowired PlatformTransactionManager manager;
  @Autowired EntityManager em;
  @Autowired TicketRepository tickets;
  @Autowired AreaRepository areas;
  @Autowired UserRepository users;

  @Test
  void roleMigrationPreservesExistingUsersAndManagementScope() {
    jdbc.execute("CREATE DATABASE role_migration_test");
    String url =
        db.getJdbcUrl().replaceAll("(?i);databaseName=[^;]*", "")
            + ";databaseName=role_migration_test";
    var source =
        new org.springframework.jdbc.datasource.DriverManagerDataSource(
            url, db.getUsername(), db.getPassword());
    var migration = org.flywaydb.core.Flyway.configure().dataSource(source).target("1").load();
    migration.migrate();
    var old = new JdbcTemplate(source);
    old.execute("INSERT INTO areas(name) VALUES ('A'),('B')");
    for (int id = 1; id <= 3; id++) {
      old.update(
          "INSERT INTO app_users(dni,username,name,password_hash,email,position,area_id) VALUES (?,?,?,?,?,?,1)",
          "0000010" + id,
          "legacy" + id,
          "Legacy",
          "test-hash",
          "legacy@example.invalid",
          "Legacy");
      old.update("INSERT INTO user_roles(user_id,role) VALUES (?,'SOLICITANTE')", id);
    }
    old.execute("INSERT INTO user_roles(user_id,role) VALUES (1,'JEFE'),(2,'JEFE'),(3,'TI')");
    old.execute("INSERT INTO chief_areas(user_id,area_id) VALUES (1,1),(2,1),(2,2)");
    old.execute(
        "INSERT INTO user_grantable_roles(user_id,role) VALUES (3,'SOLICITANTE'),(3,'JEFE'),(3,'TI')");
    org.flywaydb.core.Flyway.configure().dataSource(source).load().migrate();
    assertEquals(
        Set.of("EMPLEADO", "JEFE"),
        new HashSet<>(
            old.queryForList("SELECT role FROM user_roles WHERE user_id=1", String.class)));
    assertEquals(
        Set.of("EMPLEADO", "GERENTE"),
        new HashSet<>(
            old.queryForList("SELECT role FROM user_roles WHERE user_id=2", String.class)));
    assertEquals(3, old.queryForObject("SELECT COUNT(*) FROM app_users", Integer.class));
    assertEquals(3, old.queryForObject("SELECT COUNT(*) FROM chief_areas", Integer.class));
    assertEquals(
        Set.of("EMPLEADO", "JEFE", "GERENTE", "TI"),
        new HashSet<>(
            old.queryForList(
                "SELECT role FROM user_grantable_roles WHERE user_id=3", String.class)));
    assertEquals(
        0,
        old.queryForObject("SELECT COUNT(*) FROM roles WHERE name='SOLICITANTE'", Integer.class));
  }

  @Test
  void migrationsSequenceAndUniqueConstraints() throws Exception {
    assertEquals(
        2,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM flyway_schema_history WHERE success=1", Integer.class));
    Set<Long> values = ConcurrentHashMap.newKeySet();
    try (var executor = Executors.newFixedThreadPool(8)) {
      List<Future<?>> jobs = new ArrayList<>();
      for (int i = 0; i < 100; i++)
        jobs.add(
            executor.submit(
                () ->
                    values.add(
                        jdbc.queryForObject("SELECT NEXT VALUE FOR ticket_code_seq", Long.class))));
      for (var job : jobs) job.get();
    }
    assertEquals(100, values.size());
    assertThrows(
        org.springframework.dao.DataIntegrityViolationException.class,
        () -> jdbc.update("INSERT INTO user_roles(user_id,role) VALUES(999999,'TI')"));
  }

  @Test
  void loginLimitSharedAcrossRequests() {
    String key = TokenService.hash(UUID.randomUUID().toString());
    for (int i = 0; i < 10; i++) guard.attempt(key);
    assertEquals(
        429,
        assertThrows(com.yaret.contigo.shared.AppException.class, () -> guard.attempt(key))
            .status());
  }

  @Test
  void optimisticLockRejectsStaleUpdate() {
    TransactionTemplate tx = new TransactionTemplate(manager);
    Long id =
        tx.execute(
            s -> {
              Area a = areas.save(new Area("Área " + UUID.randomUUID()));
              User u = new User();
              u.dni = "10000001";
              u.username = "concurrent";
              u.name = "Prueba";
              u.email = "test@example.invalid";
              u.passwordHash = "not-a-production-credential";
              u.position = "Prueba";
              u.area = a;
              users.save(u);
              Ticket t = new Ticket();
              t.code = "TCK-CONCURRENT";
              t.equipment = "PC";
              t.pcUser = "pc";
              t.description = "Test";
              t.priority = Priority.MEDIA;
              t.state = TicketState.PENDIENTE;
              t.requester = u;
              t.area = a;
              t.createdAt = java.time.Instant.now();
              return tickets.saveAndFlush(t).id;
            });
    Ticket stale = tx.execute(s -> tickets.findById(id).orElseThrow());
    tx.executeWithoutResult(
        s -> {
          var fresh = tickets.findById(id).orElseThrow();
          fresh.description = "Nueva";
          tickets.saveAndFlush(fresh);
        });
    stale.description = "Anterior";
    assertThrows(
        org.springframework.orm.ObjectOptimisticLockingFailureException.class,
        () -> tx.executeWithoutResult(s -> tickets.saveAndFlush(stale)));
  }

  @Test
  void outboxRetryAndTwoWorkersDoNotSendSameClaim() throws Exception {
    jdbc.update("DELETE FROM notification_outbox");
    jdbc.update(
        "INSERT INTO notification_outbox(recipient,subject,body,status,attempts,next_attempt,created_at) VALUES('test@example.invalid','Test','Test','PENDING',0,SYSUTCDATETIME(),SYSUTCDATETIME())");
    MailGateway failing = mock(MailGateway.class);
    doThrow(new IllegalStateException("provider offline"))
        .when(failing)
        .send(anyString(), anyString(), anyString());
    new OutboxWorker(jdbc, failing).process();
    assertEquals(
        "PENDING", jdbc.queryForObject("SELECT status FROM notification_outbox", String.class));
    assertEquals(1, jdbc.queryForObject("SELECT attempts FROM notification_outbox", Integer.class));
    jdbc.update("UPDATE notification_outbox SET next_attempt=DATEADD(second,-1,SYSUTCDATETIME())");
    MailGateway gateway = mock(MailGateway.class);
    var one = new OutboxWorker(jdbc, gateway);
    var two = new OutboxWorker(jdbc, gateway);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var a = pool.submit(one::process);
      var b = pool.submit(two::process);
      a.get();
      b.get();
    }
    verify(gateway, times(1)).send(anyString(), anyString(), anyString());
    assertEquals(
        "SENT", jdbc.queryForObject("SELECT status FROM notification_outbox", String.class));
  }
}
