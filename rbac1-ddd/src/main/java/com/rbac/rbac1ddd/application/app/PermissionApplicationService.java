package com.rbac.rbac1ddd.application.app;

import static com.rbac.rbac1ddd.domain.shared.DomainException.Reason.INVALID_REQUEST;
import static com.rbac.rbac1ddd.domain.shared.DomainException.Reason.NOT_FOUND;

import com.rbac.rbac1ddd.application.auth.Operator;
import com.rbac.rbac1ddd.application.shared.OperationRecorder;
import com.rbac.rbac1ddd.application.shared.OperationTarget;
import com.rbac.rbac1ddd.domain.api.ApiRepository;
import com.rbac.rbac1ddd.domain.app.BusinessApplication;
import com.rbac.rbac1ddd.domain.app.BusinessApplicationRepository;
import com.rbac.rbac1ddd.domain.permission.PermissionDefinition;
import com.rbac.rbac1ddd.domain.permission.PermissionRepository;
import com.rbac.rbac1ddd.domain.role.Role;
import com.rbac.rbac1ddd.domain.role.RoleRepository;
import com.rbac.rbac1ddd.domain.role.RoleType;
import com.rbac.rbac1ddd.domain.shared.DomainException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 应用服务：应用权限树查询，菜单和按钮权限的登记、查看和修改，仅限总管理员。
 *
 * <p>只负责编排。菜单与按钮的上下级规则、接口关联规则在 {@link PermissionDefinition}；
 * 权限中心新权限授予总管理员由 {@link BusinessApplication#isPermissionCenter()} 和
 * {@link Role#grantNewlyRegisteredPermission(long)} 决定；权限标识重复由仓储翻译。
 */
@Service
@RequiredArgsConstructor
public class PermissionApplicationService {

  private final BusinessApplicationRepository applications;
  private final PermissionRepository permissions;
  private final ApiRepository apis;
  private final RoleRepository roles;
  private final ApiResultAssembler apiResults;
  private final OperationRecorder recorder;

  /** 应用的完整权限树：菜单按 ID 升序，各菜单下的按钮按 ID 升序。 */
  @Transactional(readOnly = true)
  public PermissionTreeResult tree(Operator operator, long applicationId) {
    operator.requireTotalAdmin();
    BusinessApplication application = requireApplication(applicationId);
    List<PermissionDefinition> entries = permissions.findByApplication(applicationId);
    Map<Long, List<PermissionTreeResult.Button>> buttons = new HashMap<>();
    for (PermissionDefinition entry : entries) {
      if (entry.getParentId() != null) {
        buttons
            .computeIfAbsent(entry.getParentId(), key -> new ArrayList<>())
            .add(new PermissionTreeResult.Button(entry.getId(), entry.getName(), entry.getCode()));
      }
    }
    return new PermissionTreeResult(
        application.getId(),
        application.getName(),
        entries.stream()
            .filter(PermissionDefinition::isMenu)
            .map(
                menu ->
                    new PermissionTreeResult.Menu(
                        menu.getId(),
                        menu.getName(),
                        menu.getCode(),
                        buttons.getOrDefault(menu.getId(), List.of())))
            .toList());
  }

  /**
   * 登记菜单或按钮权限及其接口关联，写入全局操作记录。
   *
   * <p>权限中心的新权限同时授予总管理员角色，与权限在同一事务保存。
   */
  @Transactional
  public PermissionResult create(
      Operator operator, long applicationId, PermissionCreateCommand command) {
    operator.requireTotalAdmin();
    BusinessApplication application = requireApplication(applicationId);
    PermissionDefinition parent =
        command.parentId() == null ? null : permissions.findById(command.parentId()).orElse(null);
    PermissionDefinition permission =
        PermissionDefinition.register(
            applicationId,
            command.name(),
            command.code(),
            command.type(),
            command.parentId(),
            parent);
    permission.linkApis(command.apiIds(), apis.findAllById(command.apiIds()));
    permissions.save(permission);
    if (application.isPermissionCenter()) {
      Role totalAdmin = roles.findByType(RoleType.TOTAL_ADMIN).orElseThrow();
      totalAdmin.grantNewlyRegisteredPermission(permission.getId());
      roles.save(totalAdmin);
    }
    recorder.recordGlobal(
        operator,
        "新增权限",
        "应用「" + application.getName() + "」，权限标识 " + permission.getCode(),
        OperationTarget.permission(permission));
    return result(permission);
  }

  /**
   * 修改权限名称、标识和接口关联；类型、所属应用和所属菜单不变。
   *
   * <p>提交 apiIds 时完整替换关联并记为"更新API关联"；有任何变化才写操作记录。
   */
  @Transactional
  public PermissionResult update(
      Operator operator, long applicationId, long permissionId, PermissionUpdateCommand command) {
    operator.requireTotalAdmin();
    BusinessApplication application = requireApplication(applicationId);
    PermissionDefinition permission =
        permissions
            .findInApplication(applicationId, permissionId)
            .orElseThrow(() -> new DomainException(NOT_FOUND, "该应用下不存在此权限。"));
    if (command.isEmpty()) throw new DomainException(INVALID_REQUEST, "请填写要修改的权限名称或标识。");

    List<String> changes = new ArrayList<>();
    if (command.apiIds() != null) {
      permission.linkApis(command.apiIds(), apis.findAllById(command.apiIds()));
      changes.add("更新API关联");
    }
    String oldName = permission.getName();
    if (command.name() != null && permission.rename(command.name()))
      changes.add("名称从「" + oldName + "」改为「" + command.name() + "」");
    String oldCode = permission.getCode();
    if (command.code() != null && permission.recode(command.code()))
      changes.add("标识从 " + oldCode + " 改为 " + command.code());
    permissions.save(permission);
    if (!changes.isEmpty())
      recorder.recordGlobal(
          operator,
          "修改权限",
          "应用「" + application.getName() + "」，" + String.join("，", changes),
          OperationTarget.permission(permission));
    return result(permission);
  }

  /** 查看指定应用下的权限及其完整关联接口；与 rbac1-structured 相同，应用不存在时同样提示权限不存在。 */
  @Transactional(readOnly = true)
  public PermissionResult get(Operator operator, long applicationId, long permissionId) {
    operator.requireTotalAdmin();
    return result(
        permissions
            .findInApplication(applicationId, permissionId)
            .orElseThrow(() -> new DomainException(NOT_FOUND, "权限不存在。")));
  }

  /** 读取应用，不存在时拒绝。 */
  private BusinessApplication requireApplication(long applicationId) {
    return applications
        .findById(applicationId)
        .orElseThrow(() -> new DomainException(NOT_FOUND, "应用不存在。"));
  }

  /** 权限聚合 → 结果，关联接口按 ID 升序并带上各自的关联权限。 */
  private PermissionResult result(PermissionDefinition permission) {
    return new PermissionResult(
        permission.getId(),
        permission.getApplicationId(),
        permission.getName(),
        permission.getCode(),
        permission.getType().name(),
        permission.getParentId(),
        apiResults.results(apis.findAllById(permission.getApiIds())));
  }
}
