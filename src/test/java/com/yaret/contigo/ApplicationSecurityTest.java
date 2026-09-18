package com.yaret.contigo;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.yaret.contigo.areas.*;
import com.yaret.contigo.auth.*;
import com.yaret.contigo.notifications.*;
import com.yaret.contigo.tickets.*;
import com.yaret.contigo.users.*;
import jakarta.servlet.http.Cookie;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApplicationSecurityTest {
  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry r) {
    TestKeys.configure(r);
  }

  @MockitoBean OutboxWorker worker;
  @MockitoBean LoginGuard guard;
  @Autowired MockMvc mvc;
  @Autowired UserRepository users;
  @Autowired AreaRepository areas;
  @Autowired PasswordEncoder encoder;
  @Autowired TokenService tokens;
  @Autowired RefreshRepository refresh;
  @Autowired JwtEncoder jwt;
  @Autowired JwtDecoder decoder;
  @Autowired JdbcTemplate jdbc;
  @Autowired PlatformTransactionManager manager;
  @Autowired TicketRepository tickets;
  private Long owner, chief, ti, other, outsider;
  private final String password = "Test-only-pass!123";

  @BeforeEach
  void seed() {
    assertEquals("UTC", java.util.TimeZone.getDefault().getID());
    new TransactionTemplate(manager)
        .executeWithoutResult(
            s -> {
              boolean h2 =
                  jdbc.execute(
                      (org.springframework.jdbc.core.ConnectionCallback<Boolean>)
                          connection ->
                              connection.getMetaData().getDatabaseProductName().equals("H2"));
              if (h2) {
                jdbc.execute("CREATE SEQUENCE IF NOT EXISTS ticket_code_seq START WITH 1");
                jdbc.execute(
                    "CREATE TABLE IF NOT EXISTS area_notification_recipients(area_id BIGINT,email VARCHAR(254))");
              }
              jdbc.update("DELETE FROM area_notification_recipients");
              jdbc.update("DELETE FROM ticket_history");
              jdbc.update("DELETE FROM attachments");
              jdbc.update("DELETE FROM tickets");
              jdbc.update("DELETE FROM refresh_tokens");
              jdbc.update("DELETE FROM refresh_families");
              jdbc.update("DELETE FROM notification_outbox");
              jdbc.update("DELETE FROM chief_areas");
              jdbc.update("DELETE FROM user_roles");
              jdbc.update("DELETE FROM user_grantable_roles");
              jdbc.update("DELETE FROM app_users");
              jdbc.update("DELETE FROM areas");
              Area a = areas.save(new Area("Operaciones")), b = areas.save(new Area("Otra"));
              owner = add("00000001", a, Set.of(Role.EMPLEADO), Set.of(), false);
              chief = add("00000002", a, Set.of(Role.JEFE, Role.EMPLEADO), Set.of(a, b), false);
              ti = add("00000003", b, Set.of(Role.TI, Role.EMPLEADO), Set.of(), true);
              other = add("00000004", b, Set.of(Role.EMPLEADO), Set.of(), false);
              outsider = add("00000005", b, Set.of(Role.JEFE), Set.of(b), false);
            });
  }

  private Long add(String dni, Area area, Set<Role> roles, Set<Area> managed, boolean admin) {
    User u = new User();
    u.dni = dni;
    u.username = "u" + dni;
    u.name = "Persona " + dni;
    u.email = "u" + dni + "@example.invalid";
    u.area = area;
    u.position = "TI JEFE SISTEMAS";
    u.passwordHash = encoder.encode(password);
    u.roles.addAll(roles);
    u.managedAreas.addAll(managed);
    u.manageUsers = admin;
    if (admin) u.grantableRoles.add(Role.EMPLEADO);
    return users.saveAndFlush(u).id;
  }

  private TokenService.Tokens login(String dni) {
    return tokens.login(dni, password);
  }

  private Cookie access(TokenService.Tokens token) {
    return new Cookie("ACCESS", token.access());
  }

  private record CsrfData(Cookie cookie, String token) {}

  private CsrfData csrfData() throws Exception {
    var response =
        mvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk()).andReturn().getResponse();
    String token = com.jayway.jsonpath.JsonPath.read(response.getContentAsString(), "$.token");
    String header =
        response.getHeaders("Set-Cookie").stream()
            .filter(h -> h.startsWith("XSRF-TOKEN="))
            .findFirst()
            .orElseThrow();
    return new CsrfData(
        new Cookie("XSRF-TOKEN", header.substring("XSRF-TOKEN=".length(), header.indexOf(';'))),
        token);
  }

  private org.springframework.test.web.servlet.request.RequestPostProcessor csrf()
      throws Exception {
    CsrfData data = csrfData();
    return request -> {
      var cookies = new ArrayList<Cookie>();
      if (request.getCookies() != null) cookies.addAll(Arrays.asList(request.getCookies()));
      cookies.add(data.cookie());
      request.setCookies(cookies.toArray(Cookie[]::new));
      request.addHeader("X-XSRF-TOKEN", data.token());
      return request;
    };
  }

  private long create(TokenService.Tokens token) throws Exception {
    mvc.perform(
            post("/api/v1/tickets")
                .cookie(access(token))
                .with(csrf())
                .contentType("application/json")
                .content(
                    "{\"equipment\":\"PC-001\",\"pcUser\":\"usuario.pc\",\"description\":\"Sin conexión\",\"priority\":\"ALTA\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("state").value("PENDIENTE"))
        .andExpect(
            jsonPath("code").value(org.hamcrest.Matchers.matchesPattern("TCK-[0-9]{6}-[0-9]{4,}")));
    return tickets.findAll().getLast().id;
  }

  private long version(long id) {
    return tickets.findById(id).orElseThrow().version;
  }

  private ResultActions change(
      long id, String action, TokenService.Tokens token, long version, String observation)
      throws Exception {
    return mvc.perform(
        post("/api/v1/tickets/" + id + "/" + action)
            .cookie(access(token))
            .with(csrf())
            .contentType("application/json")
            .content("{\"version\":" + version + ",\"observation\":\"" + observation + "\"}"));
  }

  @Test
  void loginCookiesCsrfAndNoSession() throws Exception {
    mvc.perform(
            post("/api/v1/auth/login")
                .contentType("application/json")
                .content("{\"dni\":\"00000001\",\"password\":\"" + password + "\"}"))
        .andExpect(status().isForbidden());
    var result =
        mvc.perform(
                post("/api/v1/auth/login")
                    .with(csrf())
                    .contentType("application/json")
                    .content("{\"dni\":\"00000001\",\"password\":\"" + password + "\"}"))
            .andExpect(status().isOk())
            .andReturn();
    String cookies = String.join(";", result.getResponse().getHeaders("Set-Cookie"));
    assertTrue(cookies.contains("HttpOnly"));
    assertTrue(cookies.contains("SameSite=Lax"));
    assertTrue(cookies.contains("Path=/api/v1/auth"));
    assertFalse(cookies.contains("JSESSIONID"));
    assertNull(result.getRequest().getSession(false));
    mvc.perform(get("/api/v1/auth/me").cookie(access(login("00000001"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("id").value(owner));
    mvc.perform(get("/api/v1/auth/csrf"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("token").isNotEmpty());
    mvc.perform(
            post("/api/v1/auth/login")
                .with(csrf())
                .contentType("application/json")
                .content("{\"dni\":\"99999999\",\"password\":\"wrong\"}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void fullWorkflowAreasFilesReportsAndConflicts() throws Exception {
    var requester = login("00000001");
    var boss = login("00000002");
    var technician = login("00000003");
    var stranger = login("00000004");
    var wrongChief = login("00000005");
    long id = create(requester);
    mvc.perform(
            post("/api/v1/tickets")
                .cookie(access(requester))
                .contentType("application/json")
                .content("{}"))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/tickets/" + id).cookie(access(stranger)))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/tickets").cookie(access(stranger)))
        .andExpect(jsonPath("totalElements").value(0));
    mvc.perform(get("/api/v1/reports/dashboard").cookie(access(stranger)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("total").value(0));
    mvc.perform(get("/api/v1/reports/dashboard").cookie(access(boss)))
        .andExpect(jsonPath("total").value(1));
    mvc.perform(get("/api/v1/tickets?size=101").cookie(access(requester)))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/api/v1/tickets?sort=passwordHash").cookie(access(requester)))
        .andExpect(status().isBadRequest());
    var file =
        new MockMultipartFile(
            "file",
            "../../evidencia.txt",
            "text/plain",
            "Evidencia segura".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    mvc.perform(
            multipart("/api/v1/tickets/" + id + "/attachments")
                .file(file)
                .cookie(access(stranger))
                .with(csrf()))
        .andExpect(status().isForbidden());
    mvc.perform(
            multipart("/api/v1/tickets/" + id + "/attachments")
                .file(file)
                .cookie(access(requester))
                .with(csrf()))
        .andExpect(status().isCreated());
    long attachment =
        jdbc.queryForObject("SELECT id FROM attachments WHERE ticket_id=?", Long.class, id);
    mvc.perform(
            get("/api/v1/tickets/" + id + "/attachments/" + attachment).cookie(access(stranger)))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/tickets/" + id + "/attachments/" + attachment).cookie(access(boss)))
        .andExpect(status().isOk())
        .andExpect(header().string("X-Content-Type-Options", "nosniff"))
        .andExpect(
            header()
                .string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")));
    change(id, "approve", wrongChief, version(id), "").andExpect(status().isForbidden());
    change(id, "close", requester, version(id), "").andExpect(status().isConflict());
    long old = version(id);
    change(id, "approve", boss, old, "").andExpect(status().isOk());
    change(id, "attend", technician, old, "Reparado").andExpect(status().isConflict());
    change(id, "attend", technician, version(id), "").andExpect(status().isBadRequest());
    mvc.perform(
            multipart("/api/v1/tickets/" + id + "/attachments")
                .file(file)
                .cookie(access(requester))
                .with(csrf()))
        .andExpect(status().isConflict());
    change(id, "attend", technician, version(id), "Cable reemplazado")
        .andExpect(status().isOk())
        .andExpect(jsonPath("state").value("ATENDIDO"));
    change(id, "close", technician, version(id), "").andExpect(status().isForbidden());
    change(id, "close", requester, version(id), "Conforme")
        .andExpect(status().isOk())
        .andExpect(jsonPath("state").value("CERRADO"));
    change(id, "reject", technician, version(id), "No").andExpect(status().isConflict());
    mvc.perform(get("/api/v1/tickets/" + id + "/history").cookie(access(requester)))
        .andExpect(jsonPath("$.length()").value(4));
    assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM notification_outbox", Integer.class) >= 4);
    var exported =
        mvc.perform(get("/api/v1/reports/excel").cookie(access(stranger)))
            .andExpect(request().asyncStarted())
            .andReturn();
    var completed = mvc.perform(asyncDispatch(exported)).andExpect(status().isOk()).andReturn();
    try (var workbook =
        org.apache.poi.ss.usermodel.WorkbookFactory.create(
            new java.io.ByteArrayInputStream(completed.getResponse().getContentAsByteArray()))) {
      assertEquals(
          "No se encontraron resultados.",
          workbook.getSheetAt(0).getRow(0).getCell(0).getStringCellValue());
    }
  }

  @Test
  void managerAndChiefShareScopeAcrossTicketsFilesDashboardAndExport() throws Exception {
    new TransactionTemplate(manager)
        .executeWithoutResult(
            s -> {
              Area a = users.findById(owner).orElseThrow().area;
              Area b = users.findById(other).orElseThrow().area;
              Area c = areas.saveAndFlush(new Area("Dirección"));
              add("00000006", c, Set.of(Role.GERENTE), Set.of(a, b), false);
              add("00000007", c, Set.of(Role.EMPLEADO), Set.of(), false);
            });
    var executive = login("00000006");
    var boss = login("00000002");
    var requester = login("00000001");
    var third = login("00000007");
    long a = create(requester), b = create(login("00000004")), c = create(third);
    mvc.perform(get("/api/v1/tickets").cookie(access(executive)))
        .andExpect(jsonPath("totalElements").value(2));
    mvc.perform(get("/api/v1/tickets").cookie(access(boss)))
        .andExpect(jsonPath("totalElements").value(1));
    mvc.perform(get("/api/v1/tickets/" + b).cookie(access(boss))).andExpect(status().isForbidden());
    change(b, "approve", boss, version(b), "").andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/tickets/" + c).cookie(access(executive)))
        .andExpect(status().isForbidden());
    change(c, "approve", executive, version(c), "").andExpect(status().isForbidden());
    var file = new MockMultipartFile("file", "proof.txt", "text/plain", "proof".getBytes());
    for (long id : new long[] {a, c}) {
      mvc.perform(
              multipart("/api/v1/tickets/" + id + "/attachments")
                  .file(file)
                  .cookie(access(id == a ? requester : third))
                  .with(csrf()))
          .andExpect(status().isCreated());
      long attachment =
          jdbc.queryForObject("SELECT id FROM attachments WHERE ticket_id=?", Long.class, id);
      mvc.perform(
              get("/api/v1/tickets/" + id + "/attachments/" + attachment).cookie(access(executive)))
          .andExpect(id == a ? status().isOk() : status().isForbidden());
    }
    mvc.perform(get("/api/v1/reports/dashboard").cookie(access(executive)))
        .andExpect(jsonPath("total").value(2));
    var export =
        mvc.perform(get("/api/v1/reports/excel").cookie(access(executive)))
            .andExpect(request().asyncStarted())
            .andReturn();
    byte[] bytes =
        mvc.perform(asyncDispatch(export))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsByteArray();
    try (var book =
        new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(bytes))) {
      assertEquals(2, book.getSheetAt(0).getLastRowNum());
    }
    change(a, "approve", executive, version(a), "").andExpect(status().isOk());
    change(b, "approve", executive, version(b), "").andExpect(status().isOk());
    change(a, "attend", executive, version(a), "Not TI").andExpect(status().isForbidden());
  }

  @Test
  void registrationEnforcesManagerAreasAndExplicitGrants() throws Exception {
    Long a =
        new TransactionTemplate(manager)
            .execute(
                s -> {
                  User operator = users.findById(ti).orElseThrow();
                  operator.grantableRoles.addAll(Set.of(Role.GERENTE, Role.JEFE));
                  return operator.area.getId();
                });
    var operator = login("00000003");
    String body =
        "{\"dni\":\"00000101\",\"username\":\"manager.test\",\"name\":\"Manager\","
            + "\"password\":\"Test-only-pass!123\",\"email\":\"manager@example.invalid\",\"position\":\"Manager\","
            + "\"areaId\":"
            + a
            + ",\"roles\":[\"GERENTE\"],\"managedAreas\":[]}";
    mvc.perform(
            post("/api/v1/users")
                .cookie(access(operator))
                .with(csrf())
                .contentType("application/json")
                .content(body))
        .andExpect(status().isBadRequest());
    String withAreas = body.replace("\"managedAreas\":[]", "\"managedAreas\":[" + a + "]");
    mvc.perform(
            post("/api/v1/users")
                .cookie(access(operator))
                .with(csrf())
                .contentType("application/json")
                .content(withAreas.replace("GERENTE", "JEFE")))
        .andExpect(status().isBadRequest());
    mvc.perform(
            post("/api/v1/users")
                .cookie(access(operator))
                .with(csrf())
                .contentType("application/json")
                .content(withAreas))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("roles[0]").value("GERENTE"));
  }

  @Test
  void evidencePreviewSupportsValidatedFormatsAndEnforcesTicketAccess() throws Exception {
    var requester = login("00000001");
    var boss = login("00000002");
    var stranger = login("00000004");
    long id = create(requester), otherId = create(stranger);
    var samples = new LinkedHashMap<String, byte[]>();
    for (String format : List.of("png", "jpg")) {
      var image = new java.awt.image.BufferedImage(2, 2, java.awt.image.BufferedImage.TYPE_INT_RGB);
      var bytes = new java.io.ByteArrayOutputStream();
      javax.imageio.ImageIO.write(image, format, bytes);
      samples.put("proof." + format, bytes.toByteArray());
    }
    try (var pdf = new org.apache.pdfbox.pdmodel.PDDocument();
        var bytes = new java.io.ByteArrayOutputStream()) {
      pdf.addPage(new org.apache.pdfbox.pdmodel.PDPage());
      pdf.save(bytes);
      samples.put("proof.pdf", bytes.toByteArray());
    }
    samples.put(
        "proof.txt",
        "Texto español <script>alert(1)</script>"
            .getBytes(java.nio.charset.StandardCharsets.UTF_8));
    Long last = null;
    for (var sample : samples.entrySet()) {
      mvc.perform(
              multipart("/api/v1/tickets/" + id + "/attachments")
                  .file(
                      new MockMultipartFile(
                          "file", sample.getKey(), "application/octet-stream", sample.getValue()))
                  .cookie(access(requester))
                  .with(csrf()))
          .andExpect(status().isCreated());
      last =
          jdbc.queryForObject("SELECT MAX(id) FROM attachments WHERE ticket_id=?", Long.class, id);
      String path = "/api/v1/tickets/" + id + "/attachments/" + last + "/view";
      String mime =
          sample.getKey().endsWith("png")
              ? "image/png"
              : sample.getKey().endsWith("jpg")
                  ? "image/jpeg"
                  : sample.getKey().endsWith("pdf")
                      ? "application/pdf"
                      : "text/plain;charset=UTF-8";
      mvc.perform(get(path).cookie(access(requester)))
          .andExpect(status().isOk())
          .andExpect(content().contentType(mime))
          .andExpect(content().bytes(sample.getValue()))
          .andExpect(
              header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("inline;")))
          .andExpect(header().string("Cache-Control", "no-store"))
          .andExpect(header().string("X-Content-Type-Options", "nosniff"))
          .andExpect(
              header()
                  .string(
                      "Content-Security-Policy",
                      org.hamcrest.Matchers.containsString("script-src 'none'")));
      mvc.perform(head(path).cookie(access(boss)))
          .andExpect(status().isOk())
          .andExpect(header().longValue("Content-Length", sample.getValue().length))
          .andExpect(content().bytes(new byte[0]));
      mvc.perform(get(path).cookie(access(stranger))).andExpect(status().isForbidden());
      mvc.perform(head(path).cookie(access(stranger))).andExpect(status().isForbidden());
      mvc.perform(get(path)).andExpect(status().isUnauthorized());
    }
    mvc.perform(get("/api/v1/tickets/" + id + "/attachments/" + last).cookie(access(requester)))
        .andExpect(status().isOk())
        .andExpect(content().contentType("application/octet-stream"))
        .andExpect(
            header()
                .string("Content-Disposition", org.hamcrest.Matchers.startsWith("attachment;")));
    mvc.perform(
            get("/api/v1/tickets/" + otherId + "/attachments/" + last + "/view")
                .cookie(access(stranger)))
        .andExpect(status().isNotFound());
    jdbc.update("UPDATE attachments SET content_type='image/svg+xml' WHERE id=?", last);
    mvc.perform(
            get("/api/v1/tickets/" + id + "/attachments/" + last + "/view")
                .cookie(access(requester)))
        .andExpect(status().isUnsupportedMediaType());
  }

  @Test
  void refreshRotationReuseAndLogout() throws Exception {
    var original = login("00000001");
    var next = tokens.rotate(original.refresh());
    assertNotEquals(original.refresh(), next.refresh());
    assertTrue(refresh.findAll().stream().noneMatch(t -> t.tokenHash.equals(original.refresh())));
    assertThrows(
        com.yaret.contigo.shared.AppException.class, () -> tokens.rotate(original.refresh()));
    assertThrows(com.yaret.contigo.shared.AppException.class, () -> tokens.rotate(next.refresh()));
    var session = login("00000001");
    mvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie("REFRESH", session.refresh())))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/api/v1/auth/refresh")
                .with(csrf())
                .cookie(new Cookie("REFRESH", session.refresh())))
        .andExpect(status().isOk());
    var logoutSession = login("00000001");
    var result =
        mvc.perform(
                post("/api/v1/auth/logout")
                    .with(csrf())
                    .cookie(new Cookie("REFRESH", logoutSession.refresh())))
            .andExpect(status().isOk())
            .andReturn();
    assertTrue(
        result.getResponse().getHeaders("Set-Cookie").stream()
            .allMatch(c -> c.contains("Max-Age=0")));
    assertThrows(
        com.yaret.contigo.shared.AppException.class, () -> tokens.rotate(logoutSession.refresh()));
    // Issued access JWT remains cryptographically valid until expiration.
    assertDoesNotThrow(() -> decoder.decode(logoutSession.access()));
  }

  @Test
  void expiredWrongAudienceAndIssuerAreRejectedAndPagesRecover() throws Exception {
    Instant now = Instant.now();
    for (var claims :
        List.of(
            JwtClaimsSet.builder()
                .subject(owner.toString())
                .issuer("contigo")
                .audience(List.of("contigo-web"))
                .issuedAt(now.minusSeconds(500))
                .expiresAt(now.minusSeconds(120))
                .build(),
            JwtClaimsSet.builder()
                .subject(owner.toString())
                .issuer("wrong")
                .audience(List.of("contigo-web"))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .build(),
            JwtClaimsSet.builder()
                .subject(owner.toString())
                .issuer("contigo")
                .audience(List.of("wrong"))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .build())) {
      String invalid =
          jwt.encode(
                  JwtEncoderParameters.from(
                      JwsHeader.with(SignatureAlgorithm.RS256).build(), claims))
              .getTokenValue();
      mvc.perform(get("/api/v1/auth/me").cookie(new Cookie("ACCESS", invalid)))
          .andExpect(status().isUnauthorized());
      mvc.perform(get("/tickets").cookie(new Cookie("ACCESS", invalid)))
          .andExpect(status().is3xxRedirection())
          .andExpect(redirectedUrlPattern("/auth/recover?next=*"));
      mvc.perform(get("/auth/recover").cookie(new Cookie("ACCESS", invalid)))
          .andExpect(status().isOk());
    }
    mvc.perform(get("/login")).andExpect(status().isOk());
    mvc.perform(get("/tickets").cookie(access(login("00000001"))))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("Consultar tickets")));
  }

  @Test
  void userCreationCannotEscalatePrivilegesAndPositionGivesNoRole() throws Exception {
    Long area = users.findById(other).orElseThrow().area.getId();
    String request =
        "{\"dni\":\"00000100\",\"username\":\"nuevo\",\"name\":\"Usuario nuevo\",\"password\":\"Test-password!123\",\"email\":\"nuevo@example.invalid\",\"areaId\":"
            + area
            + ",\"position\":\"JEFE SISTEMAS TI\",\"roles\":[\"TI\"]}";
    mvc.perform(
            post("/api/v1/users")
                .with(csrf())
                .cookie(access(login("00000001")))
                .contentType("application/json")
                .content(request))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/api/v1/users")
                .with(csrf())
                .cookie(access(login("00000003")))
                .contentType("application/json")
                .content(request))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/api/v1/users")
                .with(csrf())
                .cookie(access(login("00000003")))
                .contentType("application/json")
                .content(request.replace("[\"TI\"]", "[\"EMPLEADO\"]")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("manageUsers").value(false));
    User created = users.findByDni("00000100").orElseThrow();
    assertNotEquals("Test-password!123", created.passwordHash);
    assertTrue(encoder.matches("Test-password!123", created.passwordHash));
    long id = create(login("00000001"));
    change(id, "attend", login("00000004"), version(id), "Supuesto TI")
        .andExpect(status().isForbidden());
  }

  @Test
  void rejectionIsTerminal() throws Exception {
    long id = create(login("00000001"));
    change(id, "approve", login("00000002"), version(id), "").andExpect(status().isOk());
    change(id, "reject", login("00000003"), version(id), "Equipo fuera de alcance")
        .andExpect(status().isOk())
        .andExpect(jsonPath("state").value("RECHAZADO"));
    change(id, "close", login("00000001"), version(id), "").andExpect(status().isConflict());
  }

  @Test
  void concurrentRefreshAllowsOneRotationAndRevokesReusedFamily() throws Exception {
    var session = login("00000001");
    var start = new java.util.concurrent.CountDownLatch(1);
    var successes = new java.util.concurrent.CopyOnWriteArrayList<TokenService.Tokens>();
    var failures = new java.util.concurrent.CopyOnWriteArrayList<Integer>();
    try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
      var tasks = new ArrayList<java.util.concurrent.Future<?>>();
      for (int i = 0; i < 2; i++)
        tasks.add(
            pool.submit(
                () -> {
                  try {
                    start.await();
                    successes.add(tokens.rotate(session.refresh()));
                  } catch (com.yaret.contigo.shared.AppException e) {
                    failures.add(e.status());
                  } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                  }
                }));
      start.countDown();
      for (var task : tasks) task.get(15, java.util.concurrent.TimeUnit.SECONDS);
    }
    assertEquals(1, successes.size());
    assertEquals(List.of(401), failures);
    assertThrows(
        com.yaret.contigo.shared.AppException.class,
        () -> tokens.rotate(successes.getFirst().refresh()));
  }

  @Test
  void realCsrfCookieAndHeaderPermitLoginAndRejectForgery() throws Exception {
    CsrfData data = csrfData();
    String token = data.token();
    Cookie cookie = data.cookie();
    String body = "{\"dni\":\"00000001\",\"password\":\"" + password + "\"}";
    mvc.perform(
            post("/api/v1/auth/login")
                .cookie(cookie)
                .header("X-XSRF-TOKEN", "forged")
                .contentType("application/json")
                .content(body))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/api/v1/auth/login")
                .cookie(cookie)
                .header("X-XSRF-TOKEN", token)
                .contentType("application/json")
                .content(body))
        .andExpect(status().isOk());
    mvc.perform(get("/api/v1/auth/me").cookie(access(login("00000001"))))
        .andExpect(jsonPath("area").value("Operaciones"));
  }

  @Test
  void expiredRefreshAndUnsupportedSigningAlgorithmAreRejected() throws Exception {
    var session = login("00000001");
    jdbc.update(
        "UPDATE refresh_tokens SET expires_at=? WHERE token_hash=?",
        java.sql.Timestamp.from(Instant.now().minusSeconds(10)),
        TokenService.hash(session.refresh()));
    assertThrows(
        com.yaret.contigo.shared.AppException.class, () -> tokens.rotate(session.refresh()));
    var claims =
        JwtClaimsSet.builder()
            .subject(owner.toString())
            .issuer("contigo")
            .audience(List.of("contigo-web"))
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(300))
            .build();
    String wrongAlgorithm =
        jwt.encode(
                JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS512).build(), claims))
            .getTokenValue();
    mvc.perform(get("/api/v1/auth/me").cookie(new Cookie("ACCESS", wrongAlgorithm)))
        .andExpect(status().isUnauthorized());
    String[] segments = session.access().split("\\.");
    segments[2] = (segments[2].startsWith("A") ? "B" : "A") + segments[2].substring(1);
    mvc.perform(get("/api/v1/auth/me").cookie(new Cookie("ACCESS", String.join(".", segments))))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void everyPrivatePageRendersAndOpenApiDeclaresCookieSecurity() throws Exception {
    var session = login("00000001");
    long id = create(session);
    for (String path :
        List.of(
            "/menu",
            "/tickets/new",
            "/tickets",
            "/tickets/" + id,
            "/dashboard",
            "/reports",
            "/users/new"))
      mvc.perform(get(path).cookie(access(session))).andExpect(status().isOk());
    var openApi =
        mvc.perform(get("/v3/api-docs").cookie(access(session)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("components.securitySchemes.cookieAuth.in").value("cookie"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    java.nio.file.Files.writeString(java.nio.file.Path.of("target/openapi.json"), openApi);
    mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    mvc.perform(get("/actuator/prometheus").cookie(access(session)))
        .andExpect(status().isForbidden());
    mvc.perform(get("/actuator/prometheus").cookie(access(login("00000003"))))
        .andExpect(status().isOk());
  }

  @Test
  void exportOnlyContainsAuthorizedRowsAndTreatsFormulaAsText() throws Exception {
    var requester = login("00000001");
    long ownId = create(requester);
    create(login("00000004"));
    jdbc.update("UPDATE tickets SET description='=1+1' WHERE id=?", ownId);
    var result =
        mvc.perform(get("/api/v1/reports/excel").cookie(access(requester)))
            .andExpect(request().asyncStarted())
            .andReturn();
    var response =
        mvc.perform(asyncDispatch(result)).andExpect(status().isOk()).andReturn().getResponse();
    try (var workbook =
        org.apache.poi.ss.usermodel.WorkbookFactory.create(
            new java.io.ByteArrayInputStream(response.getContentAsByteArray()))) {
      var sheet = workbook.getSheetAt(0);
      assertEquals(1, sheet.getLastRowNum());
      assertEquals(
          tickets.findById(ownId).orElseThrow().code,
          sheet.getRow(1).getCell(0).getStringCellValue());
      assertEquals(
          org.apache.poi.ss.usermodel.CellType.STRING, sheet.getRow(1).getCell(7).getCellType());
    }
  }

  @Test
  void exportCrossesBatchBoundaryAndMonthlyGroupingUsesLima() throws Exception {
    var session = login("00000001");
    Long area = users.findById(owner).orElseThrow().area.getId();
    var rows = new ArrayList<Object[]>();
    for (int n = 0; n < 501; n++)
      rows.add(
          new Object[] {
            "BATCH-" + n,
            "PC",
            "pc",
            "Prueba",
            "MEDIA",
            "PENDIENTE",
            owner,
            area,
            java.sql.Timestamp.from(Instant.parse("2026-10-01T02:00:00Z"))
          });
    jdbc.batchUpdate(
        "INSERT INTO tickets(code,equipment,pc_user,description,priority,state,requester_id,area_id,created_at,version) VALUES(?,?,?,?,?,?,?,?,?,0)",
        rows);
    mvc.perform(get("/api/v1/reports/dashboard").cookie(access(session)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("byMonth[0].month").value("2026-09"))
        .andExpect(jsonPath("byMonth[0].count").value(501));
    var request =
        mvc.perform(get("/api/v1/reports/excel").cookie(access(session)))
            .andExpect(request().asyncStarted())
            .andReturn();
    var response =
        mvc.perform(asyncDispatch(request)).andExpect(status().isOk()).andReturn().getResponse();
    try (var workbook =
        org.apache.poi.ss.usermodel.WorkbookFactory.create(
            new java.io.ByteArrayInputStream(response.getContentAsByteArray()))) {
      assertEquals(501, workbook.getSheetAt(0).getLastRowNum());
    }
  }
}
