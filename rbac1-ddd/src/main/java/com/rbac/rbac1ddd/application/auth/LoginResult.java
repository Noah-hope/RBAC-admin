package com.rbac.rbac1ddd.application.auth;

/**
 * 登录成功的结果。
 *
 * @param accessToken 访问令牌
 * @param tokenType 令牌类型，固定为 Bearer
 * @param expiresIn 有效期秒数
 */
public record LoginResult(String accessToken, String tokenType, long expiresIn) {}
