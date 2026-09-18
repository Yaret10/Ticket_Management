package com.yaret.contigo.users;

import com.yaret.contigo.shared.AppException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class CurrentUser {
  private final UserRepository users;

  public CurrentUser(UserRepository users) {
    this.users = users;
  }

  public User get() {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName()))
      throw new AppException(401, "Inicie sesión para continuar.");
    User u =
        users
            .findById(Long.parseLong(auth.getName()))
            .orElseThrow(() -> new AppException(401, "Inicie sesión para continuar."));
    if (!u.enabled) throw new AppException(401, "Inicie sesión para continuar.");
    return u;
  }
}
