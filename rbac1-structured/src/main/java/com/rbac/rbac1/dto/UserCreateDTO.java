package com.rbac.rbac1.dto;

import static com.rbac.rbac1.dto.Formats.MAX_ID;

import cn.edu.xmu.clonefactory.CopyTo;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.rbac.rbac1.entity.UserAccount;
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
 * 新增用户的请求体。
 *
 * <p>用户名、姓名、部门和状态与 {@link UserAccount} 同名，由 CloneFactory 拷贝；初始密码只用于计算摘要，
 * 是否任命部门管理员另行处理。
 */
@CopyTo(UserAccount.class)
@Getter
@Setter
@NoArgsConstructor
public class UserCreateDTO {

  /** 登录账号，必填且不超过 32 个字符。 */
  @NotEmpty
  @CodePointLength(max = 32)
  private String username;

  /** 初始密码，只写不读。 */
  @NotEmpty
  @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
  private String initialPassword;

  /** 姓名，必填且不超过 24 个字符。 */
  @NotEmpty
  @CodePointLength(max = 24)
  private String name;

  /** 所属部门 ID。 */
  @NotNull
  @Positive
  @Max(MAX_ID)
  private Long departmentId;

  /** 账号状态，省略时为启用。 */
  @JsonSetter(nulls = Nulls.FAIL)
  @Pattern(regexp = "ACTIVE|DISABLED")
  private String status = UserAccount.ACTIVE;

  /** 是否任命为部门管理员，仅限总管理员提交。 */
  @JsonSetter(nulls = Nulls.FAIL)
  private Boolean departmentAdministrator;
}
