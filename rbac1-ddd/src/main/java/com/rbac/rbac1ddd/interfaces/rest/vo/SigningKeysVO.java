package com.rbac.rbac1ddd.interfaces.rest.vo;

import com.rbac.rbac1ddd.application.auth.SigningKeysResult;
import java.util.List;
import java.util.Map;

/**
 * 签名公钥集合的响应体，即标准 JWK Set：{@code {"keys": [...]}}。
 *
 * @param keys 每个元素是一把公钥的 JWK 字段
 */
public record SigningKeysVO(List<Map<String, Object>> keys) {

  /** 由应用层结果逐字段转换。 */
  public static SigningKeysVO from(SigningKeysResult result) {
    return new SigningKeysVO(result.keys());
  }
}
