package com.rbac.rbac1ddd.domain.request;

import static com.rbac.rbac1ddd.domain.shared.DomainException.Reason.INVALID_REQUEST;

import com.rbac.rbac1ddd.domain.permission.Permission;
import com.rbac.rbac1ddd.domain.role.RoleHierarchy;
import com.rbac.rbac1ddd.domain.shared.DomainException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 领域服务：审批一条权限申请时可选的候选业务角色。
 *
 * <p>候选角色必须在有效权限（直接 + 多层继承，跨全部应用）中包含所申请的权限；
 * 若还带有申请人尚未拥有、又不是所申请权限或其所属菜单的权限，则视为附带无关权限，不能选择。
 * 计算需要整张角色继承图和申请人的有效权限，因此不放在单个聚合中。
 */
public final class ApprovalCandidates {

  /**
   * 一个候选角色。
   *
   * @param roleId 业务角色 ID
   * @param extraPermissionIds 附带的无关权限 ID，按升序；为空时可以选择
   */
  public record Candidate(long roleId, List<Long> extraPermissionIds) {

    /** 复制列表，保证值对象不可变。 */
    public Candidate {
      extraPermissionIds = List.copyOf(extraPermissionIds);
    }

    /**
     * 没有附带无关权限时才能选择。
     *
     * @return 可以选择时为 true
     */
    public boolean selectable() {
      return extraPermissionIds.isEmpty();
    }
  }

  private final List<Candidate> candidates;

  /** 只能由 {@link #evaluate} 创建。 */
  private ApprovalCandidates(List<Candidate> candidates) {
    this.candidates = List.copyOf(candidates);
  }

  /**
   * 按当前继承图和申请人当前有效权限计算候选角色。
   *
   * @param requested 所申请的权限
   * @param applicantPermissionIds 申请人当前的有效权限 ID
   * @param hierarchy 当前角色继承图
   * @return 按角色 ID 升序排列的候选角色
   */
  public static ApprovalCandidates evaluate(
      Permission requested, Set<Long> applicantPermissionIds, RoleHierarchy hierarchy) {
    List<Candidate> result = new ArrayList<>();
    for (Long roleId : hierarchy.businessRoleIds()) {
      Set<Long> rights = hierarchy.effectivePermissionIds(roleId);
      if (!rights.contains(requested.id())) continue;
      List<Long> extra =
          rights.stream()
              .filter(id -> id != requested.id())
              .filter(id -> !id.equals(requested.parentId()))
              .filter(id -> !applicantPermissionIds.contains(id))
              .sorted()
              .toList();
      result.add(new Candidate(roleId, extra));
    }
    return new ApprovalCandidates(result);
  }

  /**
   * 全部候选角色，包括不可选择的。
   *
   * @return 按角色 ID 升序排列的候选角色
   */
  public List<Candidate> all() {
    return candidates;
  }

  /**
   * 要求所选角色是可以选择的候选角色。
   *
   * @param roleId 所选业务角色 ID
   * @return 对应的候选角色
   * @throws DomainException 不是候选角色或附带无关权限时为 INVALID_REQUEST
   */
  public Candidate requireSelectable(long roleId) {
    Candidate candidate =
        candidates.stream()
            .filter(c -> c.roleId() == roleId)
            .findFirst()
            .orElseThrow(() -> invalid("请选择包含申请权限的业务角色。"));
    if (!candidate.selectable()) throw invalid("该角色附带无关权限，请先配置最小权限角色。");
    return candidate;
  }

  /** 构造请求不符合要求的领域异常。 */
  private static DomainException invalid(String detail) {
    return new DomainException(INVALID_REQUEST, detail);
  }
}
