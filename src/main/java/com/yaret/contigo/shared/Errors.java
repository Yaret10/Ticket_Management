package com.yaret.contigo.shared;

import org.springframework.dao.*;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class Errors {
  @ExceptionHandler(org.springframework.core.task.TaskRejectedException.class)
  ProblemDetail busy(Exception e) {
    return problem(
        503, "Hay demasiadas exportaciones en curso. Inténtelo nuevamente en unos minutos.");
  }

  @ExceptionHandler(AppException.class)
  ProblemDetail application(AppException e) {
    return problem(e.status(), e.getMessage());
  }

  @ExceptionHandler({
    ObjectOptimisticLockingFailureException.class,
    DataIntegrityViolationException.class
  })
  ProblemDetail conflict(Exception e) {
    return problem(
        409, "El recurso cambió o ya existe. Actualice la página e inténtelo nuevamente.");
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ProblemDetail validation(MethodArgumentNotValidException e) {
    var p = problem(400, "Revise los campos indicados.");
    p.setProperty(
        "errors",
        e.getBindingResult().getFieldErrors().stream()
            .map(f -> f.getField() + ": " + f.getDefaultMessage())
            .toList());
    return p;
  }

  @ExceptionHandler({
    HttpMessageNotReadableException.class,
    MethodArgumentTypeMismatchException.class,
    IllegalArgumentException.class
  })
  ProblemDetail invalid(Exception e) {
    return problem(400, "La solicitud contiene datos inválidos.");
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  ProblemDetail size(Exception e) {
    return problem(413, "La evidencia supera el límite de 10 MB.");
  }

  @ExceptionHandler(Exception.class)
  ProblemDetail unexpected(Exception e) {
    org.slf4j.LoggerFactory.getLogger(Errors.class)
        .error("operation_failed exceptionType={}", e.getClass().getSimpleName());
    return problem(500, "No se pudo completar la operación. Inténtelo más tarde.");
  }

  private ProblemDetail problem(int status, String detail) {
    return ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(status), detail);
  }
}
