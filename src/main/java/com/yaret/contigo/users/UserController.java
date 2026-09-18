package com.yaret.contigo.users;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class UserController {
  private final UserService service;

  public UserController(UserService service) {
    this.service = service;
  }

  @PostMapping("/users")
  @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
  public UserView create(@Valid @RequestBody UserService.Create d) {
    return service.create(d);
  }

  @GetMapping("/auth/me")
  public UserView me() {
    return service.me();
  }

  @GetMapping("/areas")
  public List<UserService.AreaView> areas() {
    return service.areas();
  }
}
