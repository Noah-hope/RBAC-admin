package com.rbac.rbac1ddd.interfaces.rest;

import com.rbac.rbac1ddd.application.auth.SessionApplicationService;
import com.rbac.rbac1ddd.interfaces.rest.vo.SigningKeysVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 公开令牌签名公钥（JWK Set），无需登录，接入系统可据此自行校验令牌签名。 */
@RestController
@RequiredArgsConstructor
public class SigningKeyController {

  private final SessionApplicationService sessions;

  /** 返回签名公钥集合，不含私钥参数。 */
  @GetMapping("/.well-known/jwks.json")
  public SigningKeysVO publicKeys() {
    return SigningKeysVO.from(sessions.signingKeys());
  }
}
