package com.rbac.rbac1.vo;

/**
 * 登录成功的响应体。
 *
 * @param accessToken 访问令牌
 * @param tokenType 令牌类型
 * @param expiresIn 有效期（秒）
 */
public record LoginVO(String accessToken, String tokenType, long expiresIn) {}
