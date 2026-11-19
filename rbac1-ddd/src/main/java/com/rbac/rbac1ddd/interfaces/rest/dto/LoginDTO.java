package com.rbac.rbac1ddd.interfaces.rest.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.rbac.rbac1ddd.interfaces.rest.Formats;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 登录请求体（LoginRequest），校验规则与 rbac1-structured 相同。
 *
 * <p>所选应用是否已登记、是否重复属于业务规则，在验证账号密码之后由应用层检查。
 *
 * @param username 登录账号，不超过 32 个字符，兼容课程系统提交的整数形式学号
 * @param password 明文密码
 * @param systems 本次登录要访问的应用编码，至少一个
 */
public record LoginDTO(
    @JsonDeserialize(using = LoginUsernameDeserializer.class)
    @NotEmpty
    @Size(max = 32)
    String username,
    @NotEmpty
    String password,
    @NotEmpty
    List<@NotBlank @Pattern(regexp = Formats.APPLICATION_CODE) String> systems) {}
