package com.rbac.rbac1ddd.application.auth;

import java.util.List;

/**
 * 签名和签发者校验通过的令牌内容。
 *
 * @param subject 令牌主体，即用户 ID 的字符串形式
 * @param audience 令牌适用的应用编码列表
 */
public record VerifiedToken(String subject, List<String> audience) {}
