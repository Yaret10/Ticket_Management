package com.yaret.contigo.shared;

public class AppException extends RuntimeException {
  private final int status;

  public AppException(int status, String message) {
    super(message);
    this.status = status;
  }

  public int status() {
    return status;
  }

  public static AppException forbidden() {
    return new AppException(403, "No tiene permiso para realizar esta operación.");
  }

  public static AppException missing() {
    return new AppException(404, "No se encontró el recurso solicitado.");
  }
}
