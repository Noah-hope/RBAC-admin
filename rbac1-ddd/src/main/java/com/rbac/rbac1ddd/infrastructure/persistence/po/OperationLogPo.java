package com.rbac.rbac1ddd.infrastructure.persistence.po;

import cn.edu.xmu.clonefactory.CopyFrom;
import com.rbac.rbac1ddd.application.shared.OperationRecord;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 持久化对象：sys_operation_log 表的一行，记录一次管理操作及其结果。
 *
 * <p>{@code @CopyFrom(OperationRecord.class)}：CloneFactory 生成 copy(OperationLogPo, OperationRecord)。
 */
@Entity
@Table(name = "sys_operation_log")
@CopyFrom(OperationRecord.class)
@Getter
@Setter
@NoArgsConstructor
public class OperationLogPo {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
  @Column(nullable = false)
  private LocalDateTime occurredAt;

  @Column(nullable = false)
  private Long actorUserId;

  /** 操作发生时操作者的姓名快照，之后改名不影响历史记录。 */
  @Column(nullable = false, length = 24)
  private String actorName;

  @Column(nullable = false, length = 64)
  private String action;

  /** 记录可见范围：全局记录或按用户管理范围过滤的记录。 */
  @Column(nullable = false, length = 10)
  private String scope;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String detail;

  /** 操作结果：成功或失败。 */
  @Column(nullable = false, length = 7)
  private String result;
}
