package com.rbac.rbac1.dto;

import static com.rbac.rbac1.dto.Formats.MAX_ID;

import cn.edu.xmu.clonefactory.CopyTo;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.rbac.rbac1.entity.Role;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.validator.constraints.CodePointLength;
import org.hibernate.validator.constraints.UniqueElements;

/**
 * 角色配置请求体，用于新增、修改角色和角色创建申请。
 *
 * <p>名称和说明与 {@link Role} 同名，新增角色时由 CloneFactory 拷贝；直接权限和父角色另存关联表。
 */
@CopyTo(Role.class)
@Getter
@Setter
@NoArgsConstructor
public class RoleDTO {

  /** 角色名称，必填且不超过 24 个字符。 */
  @NotEmpty
  @CodePointLength(max = 24)
  private String name;

  /** 角色说明，省略时为空字符串，显式传 null 视为请求不合法。 */
  @JsonSetter(nulls = Nulls.FAIL)
  @CodePointLength(max = 48)
  private String description = "";

  /** 最终的直接权限 ID 列表，[] 表示没有直接权限。 */
  @NotNull
  @UniqueElements
  private List<@NotNull @Positive @Max(MAX_ID) Long> permissionIds;

  /** 最终的直接父角色 ID 列表，[] 表示不继承任何角色。 */
  @NotNull
  @UniqueElements
  private List<@NotNull @Positive @Max(MAX_ID) Long> inheritedRoleIds;
}
