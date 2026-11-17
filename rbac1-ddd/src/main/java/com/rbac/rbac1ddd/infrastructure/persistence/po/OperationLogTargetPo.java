package com.rbac.rbac1ddd.infrastructure.persistence.po;

import cn.edu.xmu.clonefactory.CopyFrom;
import com.rbac.rbac1ddd.application.shared.OperationTarget;
import jakarta.persistence.*;
import java.io.Serializable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 持久化对象：sys_operation_log_target 表的一行，记录一次操作涉及的一个对象。
 *
 * <p>{@code @CopyFrom(OperationTarget.class)}：CloneFactory 生成 copy(OperationLogTargetPo, OperationTarget)。
 */
@Entity
@Table(name = "sys_operation_log_target")
@IdClass(OperationLogTargetPo.Key.class)
@CopyFrom(OperationTarget.class)
@Getter
@Setter
@NoArgsConstructor
public class OperationLogTargetPo {
  @Id
  private Long operationLogId;

  /** 同一条操作记录内的对象序号，从 1 开始。 */
  @Id
  private Integer targetNo;

  @Column(nullable = false, length = 16)
  private String targetType;

  private Long targetId;

  /** 操作发生时对象名称的快照。 */
  @Column(nullable = false, length = 128)
  private String targetName;

  /** 操作发生时对象所在部门的 ID 快照，用于按管理范围筛选历史记录。 */
  private Long departmentIdAtEvent;

  /** 复合主键：操作记录 ID 与对象序号。 */
  public record Key(Long operationLogId, Integer targetNo) implements Serializable {}
}
