package com.rbac.rbac1.service;

import static com.rbac.rbac1.service.DepartmentService.requireTotalAdmin;
import static com.rbac.rbac1.service.ManagementFailure.Reason.INVALID_REQUEST;
import static com.rbac.rbac1.service.ManagementFailure.Reason.NOT_FOUND;

import cn.edu.xmu.clonefactory.util.CloneFactory;
import com.rbac.rbac1.dto.ApplicationCreateDTO;
import com.rbac.rbac1.dto.ApplicationRenameDTO;
import com.rbac.rbac1.dto.PermissionCreateDTO;
import com.rbac.rbac1.dto.PermissionUpdateDTO;
import com.rbac.rbac1.entity.BusinessApplication;
import com.rbac.rbac1.entity.OperationLogTarget;
import com.rbac.rbac1.entity.Permission;
import com.rbac.rbac1.entity.Role;
import com.rbac.rbac1.entity.RolePermission;
import com.rbac.rbac1.mapper.ApplicationMapper;
import com.rbac.rbac1.mapper.PermissionMapper;
import com.rbac.rbac1.mapper.RoleMapper;
import com.rbac.rbac1.mapper.RolePermissionMapper;
import com.rbac.rbac1.vo.ApplicationPermissionsVO;
import com.rbac.rbac1.vo.ApplicationVO;
import com.rbac.rbac1.vo.MenuNodeVO;
import com.rbac.rbac1.vo.PageVO;
import com.rbac.rbac1.vo.PermissionRefVO;
import com.rbac.rbac1.vo.PermissionVO;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 应用与权限管理：总管理员登记业务应用，维护应用下的菜单、按钮权限及其 API 关联。
 */
@Service
@RequiredArgsConstructor
public class ApplicationService {

  private static final String APPLICATION_NAME_UNIQUE = "uq_application_name";

  private static final String APPLICATION_NAME_TAKEN = "该应用名称已存在。";

  private static final String PERMISSION_CODE_UNIQUE = "uq_permission_application_code";

  private static final String PERMISSION_CODE_TAKEN = "该应用内已存在相同的权限标识。";

  private final ApplicationMapper applications;

  private final PermissionMapper permissions;

  private final OperationLogService operationLogs;

  private final ApiService apis;

  private final RoleMapper roles;

  private final RolePermissionMapper grants;

  /**
   * 分页查询已登记的应用。
   *
   * @param actor 当前操作人，必须是总管理员
   * @param page 页码，从 1 开始
   * @param pageSize 每页条数
   * @return 按 ID 排序的一页应用
   */
  @Transactional(readOnly = true)
  public PageVO<ApplicationVO> list(SessionUser actor, int page, int pageSize) {
    requireTotalAdmin(actor);
    Page<BusinessApplication> result =
        applications.findAll(PageRequest.of(page - 1, pageSize, Sort.by("id")));
    List<ApplicationVO> items =
        result.stream()
            .map(app -> new ApplicationVO(app.getId(), app.getName(), app.getCode()))
            .toList();
    return new PageVO<>(items, page, pageSize, result.getTotalElements());
  }

  /**
   * 登记应用。未给出应用编码时生成随机编码。
   *
   * @param actor 当前操作人，必须是总管理员
   * @param request 应用名称和可选编码
   * @return 登记后的应用
   * @throws ManagementFailure 应用名称重复时为 DUPLICATE
   */
  @Transactional
  public ApplicationVO create(SessionUser actor, ApplicationCreateDTO request) {
    requireTotalAdmin(actor);
    BusinessApplication app = CloneFactory.copy(new BusinessApplication(), request);
    app.setCode(request.getCode() == null ? "app-" + UUID.randomUUID() : request.getCode());
    DuplicateKeys.saveAndFlush(applications, app, APPLICATION_NAME_UNIQUE, APPLICATION_NAME_TAKEN);
    operationLogs.recordGlobalChange(
        actor,
        "新增应用",
        "登记应用「" + app.getName() + "」",
        OperationLogTarget.APPLICATION,
        app.getId(),
        app.getName());
    return new ApplicationVO(app.getId(), app.getName(), app.getCode());
  }

  /**
   * 修改应用名称。名称未变化时不保存也不写操作记录。
   *
   * @param actor 当前操作人，必须是总管理员
   * @param applicationId 应用 ID
   * @param request 新名称
   * @return 修改后的应用
   * @throws ManagementFailure 应用不存在为 NOT_FOUND，名称重复为 DUPLICATE
   */
  @Transactional
  public ApplicationVO rename(SessionUser actor, long applicationId, ApplicationRenameDTO request) {
    requireTotalAdmin(actor);
    BusinessApplication app = requireApplication(applicationId);
    if (!request.name().equals(app.getName())) {
      String detail = "名称从「" + app.getName() + "」改为「" + request.name() + "」";
      app.setName(request.name());
      DuplicateKeys.saveAndFlush(applications, app, APPLICATION_NAME_UNIQUE, APPLICATION_NAME_TAKEN);
      operationLogs.recordGlobalChange(
          actor, "修改应用", detail, OperationLogTarget.APPLICATION, app.getId(), app.getName());
    }
    return new ApplicationVO(app.getId(), app.getName(), app.getCode());
  }

  /**
   * 查询应用的权限树。
   *
   * @param actor 当前操作人，必须是总管理员
   * @param applicationId 应用 ID
   * @return 菜单及其下按钮
   * @throws ManagementFailure 应用不存在时为 NOT_FOUND
   */
  @Transactional(readOnly = true)
  public ApplicationPermissionsVO permissionTree(SessionUser actor, long applicationId) {
    requireTotalAdmin(actor);
    BusinessApplication app = requireApplication(applicationId);
    return permissionTree(app, permissions.findByApplicationIdOrderByIdAsc(applicationId));
  }

  /**
   * 把应用的权限列表组装为两级树：菜单在第一级，按钮挂在其上级菜单下，保持传入顺序。
   *
   * @param app 应用
   * @param entries 应用下的全部权限
   * @return 权限树
   */
  static ApplicationPermissionsVO permissionTree(BusinessApplication app, List<Permission> entries) {
    Map<Long, List<PermissionRefVO>> buttons = new HashMap<>();
    for (Permission entry : entries) {
      if (entry.getParentId() != null) {
        buttons
            .computeIfAbsent(entry.getParentId(), key -> new ArrayList<>())
            .add(new PermissionRefVO(entry.getId(), entry.getName(), entry.getCode()));
      }
    }
    List<MenuNodeVO> menus =
        entries.stream()
            .filter(entry -> Permission.MENU.equals(entry.getType()))
            .map(
                entry ->
                    new MenuNodeVO(
                        entry.getId(),
                        entry.getName(),
                        entry.getCode(),
                        buttons.getOrDefault(entry.getId(), List.of())))
            .toList();
    return new ApplicationPermissionsVO(app.getId(), app.getName(), menus);
  }

  /**
   * 在应用下新增权限。
   *
   * <p>菜单不能有上级，按钮的上级必须是同一应用的菜单。在本系统（权限中心）新增的权限自动授予总管理员角色，
   * 保证总管理员始终能调用全部管理接口。
   *
   * @param actor 当前操作人，必须是总管理员
   * @param applicationId 应用 ID
   * @param request 权限资料和关联的 API
   * @return 新增的权限
   * @throws ManagementFailure 应用不存在为 NOT_FOUND，上级不合法或 API 不属于该应用为 INVALID_REQUEST，
   *     权限标识重复为 DUPLICATE
   */
  @Transactional
  public PermissionVO createPermission(
      SessionUser actor, long applicationId, PermissionCreateDTO request) {
    requireTotalAdmin(actor);
    BusinessApplication app = requireApplication(applicationId);
    Long parentId = request.getParentId();
    if (Permission.MENU.equals(request.getType())) {
      if (parentId != null) {
        throw new ManagementFailure(INVALID_REQUEST, "菜单权限的上级必须为空。");
      }
    } else if (parentId == null
        || !permissions.existsByIdAndApplicationIdAndType(parentId, applicationId, Permission.MENU)) {
      throw new ManagementFailure(INVALID_REQUEST, "按钮权限的上级必须是同一应用的菜单。");
    }

    Permission permission = CloneFactory.copy(new Permission(), request);
    permission.setApplicationId(applicationId);
    DuplicateKeys.saveAndFlush(
        permissions, permission, PERMISSION_CODE_UNIQUE, PERMISSION_CODE_TAKEN);
    if (BusinessApplication.PERMISSION_CENTER.equals(app.getCode())) {
      RolePermission grant = new RolePermission();
      grant.setRoleId(roles.findByRoleType(Role.TOTAL_ADMIN).getId());
      grant.setPermissionId(permission.getId());
      grants.save(grant);
    }
    apis.replaceLinks(
        applicationId,
        permission.getId(),
        request.getApiIds() == null ? List.of() : request.getApiIds());
    operationLogs.recordGlobalChange(
        actor,
        "新增权限",
        "应用「" + app.getName() + "」，权限标识 " + permission.getCode(),
        OperationLogTarget.PERMISSION,
        permission.getId(),
        permission.getName());
    return view(permission);
  }

  /**
   * 部分修改权限的名称、标识或 API 关联；只有实际变化的项写入操作记录。
   *
   * @param actor 当前操作人，必须是总管理员
   * @param applicationId 应用 ID
   * @param permissionId 权限 ID
   * @param request 要修改的字段，至少一项
   * @return 修改后的权限
   * @throws ManagementFailure 应用或权限不存在为 NOT_FOUND，没有任何字段或 API 不合法为 INVALID_REQUEST，
   *     权限标识重复为 DUPLICATE
   */
  @Transactional
  public PermissionVO updatePermission(
      SessionUser actor, long applicationId, long permissionId, PermissionUpdateDTO request) {
    requireTotalAdmin(actor);
    BusinessApplication app = requireApplication(applicationId);
    Permission permission =
        permissions
            .findByIdAndApplicationId(permissionId, applicationId)
            .orElseThrow(() -> new ManagementFailure(NOT_FOUND, "该应用下不存在此权限。"));
    if (request.getName() == null && request.getCode() == null && request.getApiIds() == null) {
      throw new ManagementFailure(INVALID_REQUEST, "请填写要修改的权限名称或标识。");
    }

    List<String> changes = new ArrayList<>();
    if (request.getApiIds() != null) {
      apis.replaceLinks(applicationId, permissionId, request.getApiIds());
      changes.add("更新API关联");
    }
    if (request.getName() != null && !request.getName().equals(permission.getName())) {
      changes.add("名称从「" + permission.getName() + "」改为「" + request.getName() + "」");
      permission.setName(request.getName());
    }
    if (request.getCode() != null && !request.getCode().equals(permission.getCode())) {
      changes.add("标识从 " + permission.getCode() + " 改为 " + request.getCode());
      permission.setCode(request.getCode());
      DuplicateKeys.saveAndFlush(
          permissions, permission, PERMISSION_CODE_UNIQUE, PERMISSION_CODE_TAKEN);
    }
    if (!changes.isEmpty()) {
      operationLogs.recordGlobalChange(
          actor,
          "修改权限",
          "应用「" + app.getName() + "」，" + String.join("，", changes),
          OperationLogTarget.PERMISSION,
          permission.getId(),
          permission.getName());
    }
    return view(permission);
  }

  /**
   * 查询权限详情及关联的 API。
   *
   * @param actor 当前操作人，必须是总管理员
   * @param app 应用 ID
   * @param id 权限 ID
   * @return 权限详情
   * @throws ManagementFailure 权限不存在或不属于该应用时为 NOT_FOUND
   */
  @Transactional(readOnly = true)
  public PermissionVO getPermission(SessionUser actor, long app, long id) {
    requireTotalAdmin(actor);
    return view(
        permissions
            .findByIdAndApplicationId(id, app)
            .orElseThrow(() -> new ManagementFailure(NOT_FOUND, "权限不存在。")));
  }

  /** 按 ID 查找应用，不存在时抛出 NOT_FOUND。 */
  private BusinessApplication requireApplication(long id) {
    return applications
        .findById(id)
        .orElseThrow(() -> new ManagementFailure(NOT_FOUND, "应用不存在。"));
  }

  /** 组装权限响应，附带关联的 API。 */
  private PermissionVO view(Permission permission) {
    return new PermissionVO(
        permission.getId(),
        permission.getApplicationId(),
        permission.getName(),
        permission.getCode(),
        permission.getType(),
        permission.getParentId(),
        apis.forPermission(permission.getId()));
  }
}
