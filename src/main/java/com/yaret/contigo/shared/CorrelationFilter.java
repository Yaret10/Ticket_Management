package com.yaret.contigo.shared;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class CorrelationFilter extends OncePerRequestFilter {
  protected void doFilterInternal(
      HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {
    String id = UUID.randomUUID().toString();
    MDC.put("correlationId", id);
    res.setHeader("X-Correlation-ID", id);
    res.setHeader("Cache-Control", "no-store");
    try {
      chain.doFilter(req, res);
    } finally {
      MDC.remove("correlationId");
    }
  }
}
