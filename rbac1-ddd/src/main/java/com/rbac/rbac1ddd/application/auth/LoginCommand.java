package com.rbac.rbac1ddd.application.auth;

import java.util.List;

/**
 * 登录的输入，格式已在接口层校验。
 *
 * @param username 登录账号
 * @param password 明文密码
 * @param systems 本次登录要访问的应用编码，成为令牌受众
 */
public record LoginCommand(String username, String password, List<String> systems) {}
