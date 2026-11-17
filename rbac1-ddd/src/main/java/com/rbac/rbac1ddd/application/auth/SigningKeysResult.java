package com.rbac.rbac1ddd.application.auth;

import java.util.List;
import java.util.Map;

/**
 * 令牌签名公钥集合（JWK Set）。
 *
 * @param keys 每个元素是一把公钥的 JWK 字段，不含私钥参数
 */
public record SigningKeysResult(List<Map<String, Object>> keys) {}
