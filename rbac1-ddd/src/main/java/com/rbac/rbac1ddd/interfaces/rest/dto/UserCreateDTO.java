package com.rbac.rbac1ddd.interfaces.rest.dto;

import static com.rbac.rbac1ddd.interfaces.rest.Ids.MAX_ID;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.validator.constraints.CodePointLength;

/**
 * 新增用户的请求体（UserCreate）。
 *
 * <p>初始密码只用于计算摘要，不出现在任何响应中；本类不生成 toString，避免密码进入日志。
 */
@Getter
@Setter
@NoArgsConstructor
public class UserCreateDTO {

  /** 登录账号，不超过 32 个字符。 */
  @NotEmpty
  @CodePointLength(max = 32)
  private String username;

  /** 初始密码明文。 */
  @NotEmpty
  private String initialPassword;

  /** 姓名，不超过 24 个字符。 */
  @NotEmpty
  @CodePointLength(max = 24)
  private String name;

  /** 所属部门 ID。 */
  @NotNull
  @Positive
  @Max(MAX_ID)
  private Long departmentId;

  /** 初始账号状态，省略时为 ACTIVE，显式传 null 视为请求不合法。 */
  @JsonSetter(nulls = Nulls.FAIL)
  @Pattern(regexp = "ACTIVE|DISABLED")
  private String status = "ACTIVE";

  /** 是否同时任命为部门管理员，省略表示不任命；填写时（包括 false）仅限总管理员。 */
  @JsonSetter(nulls = Nulls.FAIL)
  private Boolean departmentAdministrator;
}
