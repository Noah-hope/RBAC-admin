package com.rbac.rbac1.controller;

import com.rbac.rbac1.service.JwtTokens;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
public class SigningKeyController {
  private final JwtTokens tokens;

  public SigningKeyController(JwtTokens tokens) {
    this.tokens = tokens;
  }

  @GetMapping("/.well-known/jwks.json")
  public Map<String, Object> publicKeys() {
    return tokens.publicKeys();
  }
}
