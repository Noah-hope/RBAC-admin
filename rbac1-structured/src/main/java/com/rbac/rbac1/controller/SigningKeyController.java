package com.rbac.rbac1.controller;

import com.rbac.rbac1.config.JwtTokens;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 公钥发布接口：以 JWKS 格式公开 JWT 验签公钥，业务应用可据此本地验证令牌。 */
@RestController
@RequiredArgsConstructor
public class SigningKeyController {

  private final JwtTokens tokens;

  /** 返回 JWKS，只含公钥，无需登录。 */
  @GetMapping("/.well-known/jwks.json")
  public Map<String, Object> publicKeys() {
    return tokens.publicKeys();
  }
}
