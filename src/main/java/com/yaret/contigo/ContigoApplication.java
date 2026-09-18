package com.yaret.contigo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ContigoApplication {
  static {
    // JDBC Timestamp uses the JVM zone; keep raw JDBC and Hibernate writes consistently in UTC.
    java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"));
  }

  public static void main(String[] args) {
    SpringApplication.run(ContigoApplication.class, args);
  }
}
