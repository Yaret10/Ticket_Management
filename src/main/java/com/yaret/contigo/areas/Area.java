package com.yaret.contigo.areas;

import jakarta.persistence.*;

@Entity
@Table(name = "areas")
public class Area {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @org.hibernate.annotations.Nationalized
  @Column(nullable = false, unique = true, length = 120)
  public String name;

  protected Area() {}

  public Area(String name) {
    this.name = name;
  }

  public Long getId() {
    return id;
  }

  public String getName() {
    return name;
  }
}
