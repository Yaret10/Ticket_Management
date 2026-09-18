package com.yaret.contigo.shared;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
public class PageController {
  @GetMapping("/login")
  String login() {
    return "login";
  }

  @GetMapping("/auth/recover")
  String recovery() {
    return "recover";
  }

  @GetMapping({"/", "/menu"})
  String menu(Model m) {
    m.addAttribute("title", "Inicio");
    m.addAttribute("page", "menu");
    return "menu";
  }

  @GetMapping("/tickets/new")
  String create(Model m) {
    m.addAttribute("title", "Registrar ticket");
    m.addAttribute("page", "new");
    return "new";
  }

  @GetMapping("/tickets")
  String list(Model m) {
    m.addAttribute("title", "Consultar tickets");
    m.addAttribute("page", "list");
    return "list";
  }

  @GetMapping("/tickets/{id}")
  String detail(@PathVariable Long id, Model m) {
    m.addAttribute("title", "Detalle del ticket");
    m.addAttribute("page", "detail");
    m.addAttribute("ticketId", id);
    return "detail";
  }

  @GetMapping("/dashboard")
  String dashboard(Model m) {
    m.addAttribute("title", "Dashboard");
    m.addAttribute("page", "dashboard");
    return "dashboard";
  }

  @GetMapping("/reports")
  String reports(Model m) {
    m.addAttribute("title", "Reportes");
    m.addAttribute("page", "reports");
    return "list";
  }

  @GetMapping("/users/new")
  String users(Model m) {
    m.addAttribute("title", "Registrar usuario");
    m.addAttribute("page", "users");
    return "users";
  }
}
