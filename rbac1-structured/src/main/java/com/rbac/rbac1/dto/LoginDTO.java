package com.rbac.rbac1.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 登录请求体。
 *
 * <p>所选应用是否已登记、是否重复属于业务规则，在验证账号密码之后由 Service 检查。
 *
 * @param username 登录账号，不超过 32 个字符
 * @param password 明文密码
 * @param systems 本次登录要访问的应用编码，至少一个
 */
public record LoginDTO(
    @NotEmpty
    @Size(max = 32)
    String username,
    @NotEmpty
    String password,
    @NotEmpty
    List<@NotBlank @Pattern(regexp = "[a-z][a-z0-9_-]*") String> systems) {}
