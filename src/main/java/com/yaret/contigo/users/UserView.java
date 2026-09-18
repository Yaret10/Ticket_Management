package com.yaret.contigo.users;

import java.util.Set;

public record UserView(
    Long id,
    String username,
    String name,
    String email,
    Long areaId,
    String area,
    String position,
    String employeeCode,
    Set<Role> roles,
    Set<Long> managedAreas,
    boolean manageUsers,
    Set<Role> grantableRoles) {
  public static UserView of(User u) {
    return new UserView(
        u.id,
        u.username,
        u.name,
        u.email,
        u.area.getId(),
        u.area.getName(),
        u.position,
        u.employeeCode,
        Set.copyOf(u.roles),
        u.managedAreas.stream().map(a -> a.getId()).collect(java.util.stream.Collectors.toSet()),
        u.manageUsers,
        Set.copyOf(u.grantableRoles));
  }
}
