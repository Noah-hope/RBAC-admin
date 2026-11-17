package com.rbac.rbac1ddd.domain.app;

import java.util.UUID;
import lombok.Getter;

/**
 * 聚合根：接入权限中心的业务应用。令牌受众、权限和接口登记都以应用为单位区分。
 *
 * <p>应用自己决定登记时的编码和能否改名（GRASP 信息专家）。编码创建后固定，不随显示名称变化；
 * 应用名称和编码的全局唯一由数据库约束保证，保存时由仓储翻译为业务错误。
 *
 * <p>字段不设 final（CloneFactory 不拷贝 final 字段），但只提供 getter，外部只能通过业务方法改变应用。
 */
@Getter
public class BusinessApplication {

  /** 权限中心自身的应用编码：管理接口的令牌受众，以及管理 API 登记所属的应用。 */
  public static final String PERMISSION_CENTER = "permission-center";

  /** 登记时未填写编码，系统生成的编码前缀，后接随机 UUID。 */
  public static final String GENERATED_CODE_PREFIX = "app-";

  private Long id;
  private String code;
  private String name;

  /**
   * 由仓储重建应用，或由工厂方法创建新应用。
   *
   * @param id 应用 ID，新应用为 null，保存后由 {@link #assignId} 回填
   * @param code 应用编码，登录时选择的 systems 和令牌受众使用它
   * @param name 应用名称
   */
  public BusinessApplication(Long id, String code, String name) {
    this.id = id;
    this.code = code;
    this.name = name;
  }

  /**
   * 工厂方法：登记新应用。未填写编码时生成「app-随机 UUID」，与 rbac1-structured 相同。
   *
   * @param name 应用名称
   * @param code 业务系统约定的应用编码，可以为 null
   * @return 尚未保存、ID 为 null 的应用
   */
  public static BusinessApplication register(String name, String code) {
    return new BusinessApplication(
        null, code == null ? GENERATED_CODE_PREFIX + UUID.randomUUID() : code, name);
  }

  /**
   * 新应用保存后由仓储回填数据库生成的 ID，只能回填一次。
   *
   * @param generatedId 数据库生成的 ID
   * @throws IllegalStateException 应用已有 ID 时，说明调用顺序错误
   */
  public void assignId(long generatedId) {
    if (id != null) throw new IllegalStateException("应用 ID 已存在：" + id);
    id = generatedId;
  }

  /**
   * 修改显示名称，编码保持不变。
   *
   * @param newName 新名称
   * @return 名称实际发生变化时为 true，相同名称不算修改
   */
  public boolean rename(String newName) {
    if (newName.equals(name)) return false;
    name = newName;
    return true;
  }

  /**
   * 是否为权限中心自身。权限中心新登记的权限自动授予总管理员，保证总管理员始终能使用全部管理功能。
   *
   * @return 应用编码为 {@link #PERMISSION_CENTER} 时为 true
   */
  public boolean isPermissionCenter() {
    return PERMISSION_CENTER.equals(code);
  }
}
