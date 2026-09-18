package com.yaret.contigo;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mssqlserver.MSSQLServerContainer;
import org.testcontainers.utility.DockerImageName;

/** Executes the same HTTP/security workflow against Flyway's actual SQL Server schema. */
@Testcontainers
class SqlServerWorkflowIT extends ApplicationSecurityTest {
  @Container
  static MSSQLServerContainer sql =
      new MSSQLServerContainer(
              DockerImageName.parse("mcr.microsoft.com/mssql/server:2022-CU26-ubuntu-22.04"))
          .acceptLicense();

  @DynamicPropertySource
  static void sqlProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", sql::getJdbcUrl);
    registry.add("spring.datasource.username", sql::getUsername);
    registry.add("spring.datasource.password", sql::getPassword);
    registry.add("spring.flyway.enabled", () -> "true");
    registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
  }
}
