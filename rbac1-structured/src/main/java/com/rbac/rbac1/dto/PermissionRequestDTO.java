package com.rbac.rbac1.dto;

import cn.edu.xmu.clonefactory.CopyTo;
import com.rbac.rbac1.entity.PermissionRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 提交权限申请的请求体；权限 ID 由 CloneFactory 拷贝到 {@link PermissionRequest}，申请理由去除首尾空白后单独赋值。 */
@CopyTo(PermissionRequest.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PermissionRequestDTO {

  /** 申请的权限 ID。 */
  @NotNull
  @Positive
  private Long permissionId;

  /** 申请理由，不超过 500 个字符。 */
  @NotBlank
  @Size(max = 500)
  @CopyTo.Exclude(PermissionRequest.class)
  private String reason;
}
