package com.rbac.rbac1ddd.interfaces.rest.dto;

import static com.rbac.rbac1ddd.interfaces.rest.Ids.MAX_ID;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
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
 * 创建或修改角色的请求体（RoleConfigurationInput）。
 *
 * <p>请求格式在这里校验一次：名称必填且不超过 24 个字符、说明不超过 48 个字符、两个列表必填且元素不重复。
 * 业务规则（父角色是否为业务角色、是否成环等）由领域层校验。
 */
@Getter
@Setter
@NoArgsConstructor
public class RoleDTO {

  /** 角色名称。 */
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
