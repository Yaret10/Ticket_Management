package com.yaret.contigo.shared;

import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.Map;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ApiDocumentation {
  @Bean
  OpenApiCustomizer csrfAndErrors() {
    return document -> {
      var problem =
          new Schema<>()
              .type("object")
              .addProperty("status", new Schema<Integer>().type("integer"))
              .addProperty("detail", new StringSchema())
              .addProperty("instance", new StringSchema())
              .addProperty("type", new StringSchema());
      document
          .getComponents()
          .addSchemas("ProblemDetail", problem)
          .addSecuritySchemes(
              "refreshCookie",
              new SecurityScheme()
                  .type(SecurityScheme.Type.APIKEY)
                  .in(SecurityScheme.In.COOKIE)
                  .name("REFRESH"));
      var errors =
          Map.of(
              "400",
              "Datos inválidos",
              "401",
              "Falta autenticación válida",
              "403",
              "Permiso o CSRF insuficientes",
              "404",
              "Recurso inexistente",
              "409",
              "Conflicto de estado, versión o unicidad",
              "413",
              "Archivo demasiado grande",
              "415",
              "Formato no compatible con visualización",
              "429",
              "Demasiados intentos");
      document
          .getPaths()
          .forEach(
              (path, item) -> {
                if (!path.startsWith("/api/v1")) return;
                item.readOperationsMap()
                    .forEach(
                        (method, operation) -> {
                          errors.forEach(
                              (status, description) ->
                                  operation
                                      .getResponses()
                                      .addApiResponse(
                                          status,
                                          new ApiResponse()
                                              .description(description)
                                              .content(
                                                  new Content()
                                                      .addMediaType(
                                                          "application/problem+json",
                                                          new io.swagger.v3.oas.models.media
                                                                  .MediaType()
                                                              .schema(
                                                                  new Schema<>()
                                                                      .$ref(
                                                                          "#/components/schemas/ProblemDetail"))))));
                          if (method == PathItem.HttpMethod.POST)
                            operation.addParametersItem(
                                new Parameter()
                                    .name("X-XSRF-TOKEN")
                                    .in("header")
                                    .required(true)
                                    .description(
                                        "Token de GET /api/v1/auth/csrf, junto con su cookie XSRF-TOKEN.")
                                    .schema(new StringSchema()));
                          if (path.equals("/api/v1/auth/refresh"))
                            operation.setSecurity(
                                java.util.List.of(
                                    new SecurityRequirement().addList("refreshCookie")));
                        });
              });
    };
  }
}
