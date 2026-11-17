package com.rbac.rbac1ddd.interfaces.rest.vo;

import com.rbac.rbac1ddd.application.app.PermissionResult;
import java.util.List;

/**
 * 权限及其关联接口的响应体（PermissionView）。
 *
 * @param id 权限 ID
 * @param applicationId 所属应用 ID
 * @param name 权限名称
 * @param code 权限标识
 * @param type 权限类型：MENU 或 BUTTON
 * @param parentId 按钮所属菜单 ID，菜单为 null
 * @param apis 关联的接口
 */
public record PermissionVO(
    long id,
    long applicationId,
    String name,
    String code,
    String type,
    Long parentId,
    List<ApiVO> apis) {

  /** 由应用层结果转换，关联接口逐个转换。 */
  public static PermissionVO from(PermissionResult result) {
    return new PermissionVO(
        result.id(),
        result.applicationId(),
        result.name(),
        result.code(),
        result.type(),
        result.parentId(),
        result.apis().stream().map(ApiVO::from).toList());
  }
}
