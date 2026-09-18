package com.yaret.contigo.shared;

import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.*;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class WebConfiguration {
  @Bean
  OpenAPI openAPI() {
    return new OpenAPI()
        .info(
            new Info()
                .title("conTIgo API")
                .version("v1")
                .description(
                    "Autenticación cookie ACCESS HttpOnly JWT RS256. Obtenga GET /api/v1/auth/csrf y envíe su token en X-XSRF-TOKEN para todo POST, incluso login, refresh y logout. Cookies same-origin; no Bearer header. Paginación base cero, size 1..100, sort: createdAt, code, priority, state. Errores ProblemDetail: 400 validación, 401 autenticación, 403 permiso/CSRF, 404 inexistente, 409 estado/concurrencia, 413 tamaño, 429 intentos."))
        .components(
            new Components()
                .addSecuritySchemes(
                    "cookieAuth",
                    new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.COOKIE)
                        .name("ACCESS")))
        .addSecurityItem(new SecurityRequirement().addList("cookieAuth"));
  }
}
