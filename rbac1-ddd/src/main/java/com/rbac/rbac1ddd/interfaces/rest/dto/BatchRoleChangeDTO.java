package com.rbac.rbac1ddd.interfaces.rest.dto;

import static com.rbac.rbac1ddd.interfaces.rest.Ids.MAX_ID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.hibernate.validator.constraints.UniqueElements;

/**
 * 批量分配或撤销业务角色的请求体。
 *
 * @param action GRANT 表示分配，REVOKE 表示撤销
 * @param userIds 所选用户，至少一个且不重复
 * @param roleIds 所选业务角色，至少一个且不重复
 */
public record BatchRoleChangeDTO(
    @NotNull
    @Pattern(regexp = "GRANT|REVOKE")
    String action,
    @NotEmpty
    @UniqueElements
    List<@NotNull @Positive @Max(MAX_ID) Long> userIds,
    @NotEmpty
    @UniqueElements
    List<@NotNull @Positive @Max(MAX_ID) Long> roleIds) {}
