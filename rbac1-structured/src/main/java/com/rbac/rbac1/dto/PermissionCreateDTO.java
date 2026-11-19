package com.rbac.rbac1.dto;

import static com.rbac.rbac1.dto.Formats.MAX_ID;

import cn.edu.xmu.clonefactory.CopyTo;
import com.rbac.rbac1.entity.Permission;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.validator.constraints.CodePointLength;
import org.hibernate.validator.constraints.UniqueElements;

/** 登记权限的请求体；名称、标识、类型和上级与 {@link Permission} 同名，由 CloneFactory 拷贝。 */
@CopyTo(Permission.class)
@Getter
@Setter
@NoArgsConstructor
public class PermissionCreateDTO {

  /** 权限名称，必填且不超过 24 个字符。 */
  @NotEmpty
  @CodePointLength(max = 24)
  private String name;

  /** 权限标识，格式为「资源:动作」。 */
  @NotNull
  @CodePointLength(max = 64)
  @Pattern(regexp = Formats.PERMISSION_CODE)
  private String code;

  /** 权限类型：菜单或按钮。 */
  @NotNull
  @Pattern(regexp = "MENU|BUTTON")
  private String type;

  /** 按钮所属菜单的 ID。 */
  @Positive
  @Max(MAX_ID)
  private Long parentId;

  /** 关联的接口 ID；不提交表示不关联接口。 */
  @UniqueElements
  private List<@NotNull @Positive Long> apiIds;
}
