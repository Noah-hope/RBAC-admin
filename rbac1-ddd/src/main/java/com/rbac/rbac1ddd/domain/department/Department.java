package com.rbac.rbac1ddd.domain.department;

import java.util.Objects;
import lombok.Getter;

/**
 * 聚合根：部门（公司、分公司、部门统一为一种有上下级关系的组织）。
 *
 * <p>部门自己完成改名和调整上级（GRASP 信息专家）；"上级不能是本部门或其下级部门"需要整棵树，
 * 由调用方传入 {@link DepartmentTree} 判断。
 *
 * <p>字段不设 final（CloneFactory 不拷贝 final 字段），但只提供 getter，外部只能通过业务方法改变部门。
 */
@Getter
public class Department {
  private Long id;
  private String name;
  private Long parentId;

  /**
   * 由仓储重建部门聚合。
   *
   * @param id 部门 ID，新部门为 null，保存后由 {@link #assignId} 回填
   * @param name 部门名称
   * @param parentId 上级部门 ID，顶级部门为 null
   */
  public Department(Long id, String name, Long parentId) {
    this.id = id;
    this.name = name;
    this.parentId = parentId;
  }

  /**
   * 工厂方法：新增部门。
   *
   * @param name 部门名称
   * @param parentId 已确认存在的上级部门 ID，顶级部门为 null
   * @return 尚未保存、ID 为 null 的部门
   */
  public static Department create(String name, Long parentId) {
    return new Department(null, name, parentId);
  }

  /**
   * 新部门保存后由仓储回填数据库生成的 ID，只能回填一次。
   *
   * @param generatedId 数据库生成的 ID
   * @throws IllegalStateException 部门已有 ID 时，说明调用顺序错误
   */
  public void assignId(long generatedId) {
    if (id != null) throw new IllegalStateException("部门 ID 已存在：" + id);
    id = generatedId;
  }

  /**
   * 修改部门名称。
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
   * 调整上级部门。上级没有变化时不做任何校验；变化时上级必须存在，且不能是本部门或其下级部门。
   *
   * @param newParentId 新的上级部门 ID，null 表示改为顶级部门
   * @param tree 当前部门树
   * @return 上级实际发生变化时为 true
   * @throws com.rbac.rbac1ddd.domain.shared.DomainException 上级不存在（NOT_FOUND）或形成循环（INVALID_REQUEST）
   */
  public boolean moveUnder(Long newParentId, DepartmentTree tree) {
    if (Objects.equals(newParentId, parentId)) return false;
    tree.ensureCanMove(id, newParentId);
    parentId = newParentId;
    return true;
  }
}
