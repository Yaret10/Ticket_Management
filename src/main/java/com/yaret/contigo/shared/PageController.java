package com.yaret.contigo.shared;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

@Controller
public class PageController {
  @GetMapping("/login")
  String login() {
    return "forward:/index.html";
  }

  @GetMapping("/auth/recover")
  String recovery() {
    return "forward:/index.html";
  }

  @GetMapping({"/", "/menu"})
  String menu() {
    return "forward:/index.html";
  }

  @GetMapping("/tickets/new")
  String create() {
    return "forward:/index.html";
  }

  @GetMapping("/tickets")
  String list() {
    return "forward:/index.html";
  }

  @GetMapping("/tickets/{id}")
  String detail(@PathVariable Long id) {
    return "forward:/index.html";
  }

  @GetMapping("/dashboard")
  String dashboard() {
    return "forward:/index.html";
  }

  @GetMapping("/reports")
  String reports() {
    return "forward:/index.html";
  }

  @GetMapping("/users/new")
  String users() {
    return "forward:/index.html";
  }
}
