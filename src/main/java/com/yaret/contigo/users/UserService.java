package com.yaret.contigo.users;

import com.yaret.contigo.areas.*;
import com.yaret.contigo.shared.AppException;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {
  public record Create(
      @Pattern(regexp = "[0-9]{8}") @NotBlank String dni,
      @NotBlank @Size(max = 100) String username,
      @NotBlank @Size(max = 120) String name,
      @NotBlank @Size(min = 12, max = 72) String password,
      @NotBlank @Email @Size(max = 254) String email,
      @NotNull Long areaId,
      @NotBlank @Size(max = 120) String position,
      @Size(max = 100) String employeeCode,
      @NotEmpty Set<Role> roles,
      Set<Long> managedAreas) {}

  private final UserRepository users;
  private final AreaRepository areas;
  private final CurrentUser current;
  private final PasswordEncoder encoder;

  public UserService(
      UserRepository users, AreaRepository areas, CurrentUser current, PasswordEncoder encoder) {
    this.users = users;
    this.areas = areas;
    this.current = current;
    this.encoder = encoder;
  }

  @Transactional
  public UserView create(Create d) {
    User actor = current.get();
    if (!actor.has(Role.TI) || !actor.manageUsers || !actor.grantableRoles.containsAll(d.roles()))
      throw AppException.forbidden();
    if (d.password().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
      throw new AppException(400, "La contraseña no puede superar 72 bytes en UTF-8.");
    if (!d.roles().contains(Role.GERENTE)
        && d.managedAreas() != null
        && !d.managedAreas().isEmpty())
      throw new AppException(
          400,
          "Solo un Gerente puede tener varias áreas de gestión. Jefe gestiona su área principal.");
    if (d.roles().contains(Role.GERENTE)
        && (d.managedAreas() == null || d.managedAreas().isEmpty()))
      throw new AppException(400, "Seleccione al menos un área de gestión para el Gerente.");
    User u = new User();
    u.dni = d.dni();
    u.username = d.username();
    u.name = d.name();
    u.passwordHash = encoder.encode(d.password());
    u.email = d.email();
    u.area = areas.findById(d.areaId()).orElseThrow(AppException::missing);
    u.position = d.position();
    u.employeeCode = d.employeeCode();
    u.roles.addAll(d.roles());
    if (d.managedAreas() != null)
      for (Long id : d.managedAreas())
        u.managedAreas.add(areas.findById(id).orElseThrow(AppException::missing));
    // Delegation of user administration is an operator action; it cannot be granted through
    // registration.
    return UserView.of(users.saveAndFlush(u));
  }

  @Transactional(readOnly = true)
  public UserView me() {
    return UserView.of(current.get());
  }

  @Transactional(readOnly = true)
  public List<AreaView> areas() {
    current.get();
    return areas.findAll().stream().map(a -> new AreaView(a.id, a.name)).toList();
  }

  public record AreaView(Long id, String name) {}
}
