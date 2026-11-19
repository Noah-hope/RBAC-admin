package com.rbac.rbac1.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** sys_operation_log_target 表的一行：操作记录涉及的一个对象快照，记录发生时的名称和部门。 */
@Entity
@Table(name = "sys_operation_log_target")
@IdClass(OperationLogTarget.Key.class)
@Getter
@Setter
@NoArgsConstructor
public class OperationLogTarget {

  /** 对象类型：用户。 */
  public static final String USER = "USER";

  /** 对象类型：部门。 */
  public static final String DEPARTMENT = "DEPARTMENT";

  /** 对象类型：角色。 */
  public static final String ROLE = "ROLE";

  /** 对象类型：业务应用。 */
  public static final String APPLICATION = "APPLICATION";

  /** 对象类型：权限。 */
  public static final String PERMISSION = "PERMISSION";

  /** 对象类型：接口；失败记录也用它记录请求路由。 */
  public static final String API = "API";

  /** 对象类型：权限申请。 */
  public static final String PERMISSION_REQUEST = "PERMISSION_REQ";

  /** 对象类型：角色创建申请。 */
  public static final String ROLE_REQUEST = "ROLE_REQUEST";

  @Id
  private Long operationLogId;

  @Id
  private Integer targetNo;

  private String targetType;

  private Long targetId;

  private String targetName;

  private Long departmentIdAtEvent;

  /**
   * 复合主键。
   *
   * @param operationLogId 操作记录 ID
   * @param targetNo 对象在该记录内的序号
   */
  public record Key(Long operationLogId, Integer targetNo) implements Serializable {}
}
