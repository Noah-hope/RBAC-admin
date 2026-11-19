package com.rbac.rbac1.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** sys_operation_log 表的一行：一条操作记录，按可见范围分为全局记录和用户范围记录。 */
@Entity
@Table(name = "sys_operation_log")
@Getter
@Setter
@NoArgsConstructor
public class OperationLog {

  /** 可见范围：仅总管理员可见的全局配置变更。 */
  public static final String GLOBAL = "GLOBAL";

  /** 可见范围：按目标用户所在部门授权查看的用户变更。 */
  public static final String USER_SCOPE = "USER_SCOPE";

  /** 结果：操作成功。 */
  public static final String SUCCESS = "SUCCESS";

  /** 结果：操作失败。 */
  public static final String FAILURE = "FAILURE";

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
  private LocalDateTime occurredAt;

  private Long actorUserId;

  private String actorName;

  private String action;

  private String scope;

  @Column(columnDefinition = "TEXT")
  private String detail;

  private String result;
}
