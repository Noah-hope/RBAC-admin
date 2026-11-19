package com.rbac.rbac1.service;

import static com.rbac.rbac1.service.DepartmentService.isTotalAdmin;
import static com.rbac.rbac1.service.DepartmentService.requireAdministrator;
import static com.rbac.rbac1.service.DepartmentService.requireTotalAdmin;
import static com.rbac.rbac1.service.ManagementFailure.Reason.CONFLICT;
import static com.rbac.rbac1.service.ManagementFailure.Reason.FORBIDDEN;
import static com.rbac.rbac1.service.ManagementFailure.Reason.NOT_FOUND;

import com.rbac.rbac1.dto.RoleCreationReviewDTO;
import com.rbac.rbac1.dto.RoleDTO;
import com.rbac.rbac1.entity.OperationLogTarget;
import com.rbac.rbac1.entity.Role;
import com.rbac.rbac1.entity.RoleCreationRequest;
import com.rbac.rbac1.entity.RoleCreationRequestInheritance;
import com.rbac.rbac1.entity.RoleCreationRequestPermission;
import com.rbac.rbac1.mapper.RoleCreationRequestInheritanceMapper;
import com.rbac.rbac1.mapper.RoleCreationRequestMapper;
import com.rbac.rbac1.mapper.RoleCreationRequestPermissionMapper;
import com.rbac.rbac1.mapper.UserMapper;
import com.rbac.rbac1.vo.EffectivePermissionVO;
import com.rbac.rbac1.vo.PageVO;
import com.rbac.rbac1.vo.RequestInheritedRoleVO;
import com.rbac.rbac1.vo.RoleCreationDetailsVO;
import com.rbac.rbac1.vo.RoleCreationRequestVO;
import com.rbac.rbac1.vo.RoleDetailsVO;
import com.rbac.rbac1.vo.RoleRefVO;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 新建角色申请：部门管理员提交新业务角色的配置，由总管理员审核；批准时由总管理员确认的配置创建角色。
 *
 * <p>申请保存父角色的名称快照，父角色后来被删除时仍能显示，并据此停止有效权限预览。
 */
@Service
@RequiredArgsConstructor
public class RoleCreationRequestService {

  private final RoleCreationRequestMapper requests;

  private final RoleCreationRequestPermissionMapper links;

  private final RoleService roles;

  private final RoleCreationRequestInheritanceMapper parents;

  private final RolePermissionService effective;

  private final UserMapper users;

  private final OperationLogService logs;

  /**
   * 提交新建角色申请。配置按新增角色的同一套规则校验。
   *
   * @param actor 当前操作人，必须是部门管理员
   * @param input 待建角色的配置
   * @return 提交后的申请
   * @throws ManagementFailure 不是部门管理员为 FORBIDDEN，配置不合法为 INVALID_REQUEST
   */
  @Transactional
  public RoleCreationRequestVO submit(SessionUser actor, RoleDTO input) {
    if (!Role.DEPARTMENT_ADMIN.equals(actor.managementRole())) {
      throw new ManagementFailure(FORBIDDEN, "仅部门管理员提交角色创建申请。");
    }
    List<RoleRefVO> selectedParents = roles.validateNew(input);
    RoleCreationRequest request = new RoleCreationRequest();
    request.setApplicantUserId(actor.id());
    request.setRoleName(input.getName());
    request.setRoleDescription(input.getDescription());
    request.setStatus(RoleCreationRequest.PENDING);
    request.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
    requests.save(request);
    links.saveAll(
        input.getPermissionIds().stream()
            .map(
                id -> {
                  RoleCreationRequestPermission link = new RoleCreationRequestPermission();
                  link.setRequestId(request.getId());
                  link.setPermissionId(id);
                  return link;
                })
            .toList());
    parents.saveAll(
        selectedParents.stream()
            .map(
                parent -> {
                  RoleCreationRequestInheritance link = new RoleCreationRequestInheritance();
                  link.setRequestId(request.getId());
                  link.setInheritedRoleId(parent.id());
                  link.setInheritedRoleName(parent.name());
                  return link;
                })
            .toList());
    log(actor, request, "提交角色创建申请");
    return view(request);
  }

  /**
   * 分页查询新建角色申请：总管理员看全部，部门管理员只看自己提交的。
   *
   * @param actor 当前操作人，必须是管理员
   * @param status 只查该状态，可为 {@code null}
   * @param page 页码，从 1 开始
   * @param size 每页条数
   * @return 按提交时间倒序的一页申请
   */
  @Transactional(readOnly = true)
  public PageVO<RoleCreationRequestVO> list(SessionUser actor, String status, int page, int size) {
    requireAdministrator(actor);
    Page<RoleCreationRequest> found =
        requests.findAll(
            (root, query, cb) -> {
              List<Predicate> conditions = new ArrayList<>();
              if (!isTotalAdmin(actor)) {
                conditions.add(cb.equal(root.get("applicantUserId"), actor.id()));
              }
              if (status != null) {
                conditions.add(cb.equal(root.get("status"), status));
              }
              return cb.and(conditions.toArray(Predicate[]::new));
            },
            PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createdAt", "id")));
    return new PageVO<>(
        views(found.getContent(), effective.load()), page, size, found.getTotalElements());
  }

  /**
   * 查询申请详情及按当前关系图预览的有效权限。
   *
   * <p>部门管理员查询他人的申请时按不存在处理，不暴露申请是否存在。有父角色已被删除时预览为 {@code null}。
   *
   * @param actor 当前操作人
   * @param id 申请 ID
   * @return 申请详情和有效权限预览
   * @throws ManagementFailure 申请不存在或不可见时为 NOT_FOUND
   */
  @Transactional(readOnly = true)
  public RoleCreationDetailsVO get(SessionUser actor, long id) {
    RoleCreationRequest request = requireRequest(id);
    if (!isTotalAdmin(actor) && request.getApplicantUserId() != actor.id()) {
      throw new ManagementFailure(NOT_FOUND, "申请不存在。");
    }
    RolePermissionService.Graph graph = effective.load();
    RoleCreationRequestVO original = views(List.of(request), graph).getFirst();
    List<EffectivePermissionVO> preview =
        original.inheritedRoles().stream().anyMatch(parent -> !parent.exists())
            ? null
            : effective.preview(
                graph,
                Set.copyOf(original.permissionIds()),
                original.inheritedRoles().stream().map(RequestInheritedRoleVO::id).toList());
    return new RoleCreationDetailsVO(original, preview);
  }

  /**
   * 审核待处理的申请。批准时按审核人提交的角色配置创建角色，配置可以与申请不同。
   *
   * @param actor 当前操作人，必须是总管理员
   * @param id 申请 ID
   * @param input 审核决定和批准时的角色配置
   * @return 审核后的申请
   * @throws ManagementFailure 申请不存在为 NOT_FOUND，已处理为 CONFLICT，角色配置不合法或名称重复
   */
  @Transactional
  public RoleCreationRequestVO review(SessionUser actor, long id, RoleCreationReviewDTO input) {
    requireTotalAdmin(actor);
    RoleCreationRequest request = requireRequest(id);
    if (!RoleCreationRequest.PENDING.equals(request.getStatus())) {
      throw new ManagementFailure(CONFLICT, "申请已处理。");
    }
    if ("APPROVE".equals(input.decision())) {
      RoleDetailsVO role = roles.create(actor, input.role());
      request.setCreatedRoleId(role.id());
      request.setStatus(RoleCreationRequest.APPROVED);
    } else {
      request.setStatus(RoleCreationRequest.REJECTED);
    }
    request.setReviewerUserId(actor.id());
    request.setReviewedAt(LocalDateTime.now(ZoneOffset.UTC));
    log(actor, request, "审核角色创建申请");
    return view(request);
  }

  /** 按 ID 查找申请，不存在时抛出 NOT_FOUND。 */
  private RoleCreationRequest requireRequest(long id) {
    return requests
        .findById(id)
        .orElseThrow(() -> new ManagementFailure(NOT_FOUND, "申请不存在。"));
  }

  /** 组装单个申请的响应。 */
  private RoleCreationRequestVO view(RoleCreationRequest r) {
    return views(List.of(r), effective.load()).getFirst();
  }

  /**
   * 批量组装申请响应，一次查出申请人姓名、申请的权限和父角色快照；父角色是否仍存在以关系图为准。
   */
  private List<RoleCreationRequestVO> views(
      List<RoleCreationRequest> rows, RolePermissionService.Graph graph) {
    if (rows.isEmpty()) {
      return List.of();
    }
    List<Long> ids = rows.stream().map(r -> r.getId()).toList();
    Map<Long, String> names =
        users.findAllById(rows.stream().map(r -> r.getApplicantUserId()).distinct().toList()).stream()
            .collect(Collectors.toMap(u -> u.getId(), u -> u.getName()));
    Map<Long, List<Long>> direct =
        links.findByRequestIdInOrderByPermissionId(ids).stream()
            .collect(
                Collectors.groupingBy(
                    link -> link.getRequestId(),
                    Collectors.mapping(link -> link.getPermissionId(), Collectors.toList())));
    Map<Long, List<RequestInheritedRoleVO>> snapshots =
        parents.findByRequestIdInOrderByInheritedRoleId(ids).stream()
            .collect(
                Collectors.groupingBy(
                    link -> link.getRequestId(),
                    Collectors.mapping(
                        link ->
                            new RequestInheritedRoleVO(
                                link.getInheritedRoleId(),
                                link.getInheritedRoleName(),
                                graph.roles().containsKey(link.getInheritedRoleId())),
                        Collectors.toList())));
    return rows.stream()
        .map(
            r ->
                new RoleCreationRequestVO(
                    r.getId(),
                    r.getApplicantUserId(),
                    names.get(r.getApplicantUserId()),
                    r.getRoleName(),
                    r.getRoleDescription(),
                    direct.getOrDefault(r.getId(), List.of()),
                    snapshots.getOrDefault(r.getId(), List.of()),
                    r.getStatus(),
                    r.getReviewerUserId(),
                    r.getCreatedRoleId(),
                    r.getCreatedAt().toInstant(ZoneOffset.UTC),
                    r.getReviewedAt() == null ? null : r.getReviewedAt().toInstant(ZoneOffset.UTC)))
        .toList();
  }

  /** 写入申请相关的全局操作记录，批准时附带实际创建的角色 ID。 */
  private void log(SessionUser actor, RoleCreationRequest r, String action) {
    logs.recordGlobalChange(
        actor,
        action,
        "状态 "
            + r.getStatus()
            + (r.getCreatedRoleId() == null ? "" : "，实际创建角色 ID " + r.getCreatedRoleId()),
        OperationLogTarget.ROLE_REQUEST,
        r.getId(),
        r.getRoleName());
  }
}
