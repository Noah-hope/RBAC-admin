package com.rbac.rbac1ddd.domain.shared;

/** 领域规则被违反时抛出。接口层统一把它转换为 HTTP 错误响应。 */
public class DomainException extends RuntimeException {

  /** 违反规则的原因分类，接口层据此选择 HTTP 状态码和错误码。 */
  public enum Reason {
    /** 操作本身不被允许，例如修改预置管理角色。 */
    FORBIDDEN,
    /** 引用的对象不存在。 */
    NOT_FOUND,
    /** 当前状态不允许操作，例如删除仍被用户持有的角色。 */
    CONFLICT,
    /** 数据已存在，例如角色名称重复。 */
    DUPLICATE,
    /** 请求内容不符合业务规则，例如循环继承、按钮缺少所属菜单。 */
    INVALID_REQUEST,
    /** 目标用户超出操作者的部门管理范围。 */
    OUTSIDE_MANAGEMENT_SCOPE,
    /** 令牌所属应用与被访问的应用不一致。 */
    APPLICATION_MISMATCH
  }

  private final Reason reason;

  /**
   * 创建领域异常。
   *
   * @param reason 原因分类
   * @param detail 面向用户的中文提示，原样返回给前端
   */
  public DomainException(Reason reason, String detail) {
    super(detail);
    this.reason = reason;
  }

  /**
   * 返回原因分类。沿用 record 风格的方法名，调用方已按此名称使用。
   *
   * @return 原因分类
   */
  public Reason reason() {
    return reason;
  }
}
