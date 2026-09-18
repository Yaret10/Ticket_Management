package com.yaret.contigo.users;

import com.yaret.contigo.areas.Area;
import jakarta.persistence.*;
import java.util.*;

@Entity
@Table(name = "app_users")
public class User {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Version public long version;

  @Column(nullable = false, unique = true, length = 8)
  public String dni;

  @org.hibernate.annotations.Nationalized
  @Column(nullable = false, unique = true, length = 100)
  public String username;

  @org.hibernate.annotations.Nationalized
  @Column(nullable = false, length = 120)
  public String name;

  @Column(nullable = false, length = 255)
  public String passwordHash;

  @org.hibernate.annotations.Nationalized
  @Column(nullable = false, length = 254)
  public String email;

  @org.hibernate.annotations.Nationalized
  @Column(nullable = false, length = 120)
  public String position;

  @org.hibernate.annotations.Nationalized
  @Column(length = 100)
  public String employeeCode;

  public boolean enabled = true;
  public boolean manageUsers;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "area_id")
  public Area area;

  @ElementCollection
  @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
  @Enumerated(EnumType.STRING)
  @Column(name = "role", length = 20)
  public Set<Role> roles = new HashSet<>();

  @ElementCollection
  @CollectionTable(name = "user_grantable_roles", joinColumns = @JoinColumn(name = "user_id"))
  @Enumerated(EnumType.STRING)
  @Column(name = "role", length = 20)
  public Set<Role> grantableRoles = new HashSet<>();

  @ManyToMany
  @JoinTable(
      name = "chief_areas",
      joinColumns = @JoinColumn(name = "user_id"),
      inverseJoinColumns = @JoinColumn(name = "area_id"))
  public Set<Area> managedAreas = new HashSet<>();

  public boolean has(Role role) {
    return roles.contains(role);
  }

  public Long getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public String getEmail() {
    return email;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public Set<Role> getRoles() {
    return roles;
  }
}
