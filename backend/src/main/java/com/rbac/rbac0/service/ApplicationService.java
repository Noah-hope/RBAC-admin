package com.rbac.rbac0.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.rbac.rbac0.dto.ApplicationDtos.ApplicationInput;
import com.rbac.rbac0.dto.ApplicationDtos.ApplicationPage;
import com.rbac.rbac0.dto.ApplicationDtos.ApplicationPermissions;
import com.rbac.rbac0.dto.ApplicationDtos.ApplicationView;
import com.rbac.rbac0.dto.ApplicationDtos.MenuNode;
import com.rbac.rbac0.dto.ApplicationDtos.PermissionCreate;
import com.rbac.rbac0.dto.ApplicationDtos.PermissionRef;
import com.rbac.rbac0.dto.ApplicationDtos.PermissionUpdate;
import com.rbac.rbac0.dto.ApplicationDtos.PermissionView;
import com.rbac.rbac0.dto.SessionDtos.SessionUser;
import com.rbac.rbac0.entity.BusinessApplication;
import com.rbac.rbac0.entity.Permission;
import com.rbac.rbac0.mapper.ApplicationMapper;
import com.rbac.rbac0.mapper.PermissionMapper;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.rbac.rbac0.service.DepartmentService.requireTotalAdmin;
import static com.rbac.rbac0.service.ManagementFailure.Reason.INVALID_REQUEST;
import static com.rbac.rbac0.service.ManagementFailure.Reason.NOT_FOUND;

@Service
public class ApplicationService {

    private static final String MENU = "MENU";

    private final ApplicationMapper applications;
    private final PermissionMapper permissions;
    private final OperationLogService operationLogs;

    public ApplicationService(ApplicationMapper applications, PermissionMapper permissions,
                              OperationLogService operationLogs) {
        this.applications = applications;
        this.permissions = permissions;
        this.operationLogs = operationLogs;
    }

    @Transactional(readOnly = true)
    public ApplicationPage list(SessionUser actor, int page, int pageSize) {
        requireTotalAdmin(actor);
        var result = applications.findAll(PageRequest.of(page - 1, pageSize, Sort.by("id")));
        var items = result.stream().map(app -> new ApplicationView(app.id, app.name)).toList();
        return new ApplicationPage(items, page, pageSize, result.getTotalElements());
    }

    @Transactional
    public ApplicationView create(SessionUser actor, ApplicationInput request) {
        requireTotalAdmin(actor);
        var app = new BusinessApplication();
        app.name = request.name();
        applications.save(app);
        operationLogs.recordGlobalChange(actor, "新增应用", "登记应用「" + app.name + "」",
                "APPLICATION", app.id, app.name);
        return new ApplicationView(app.id, app.name);
    }

    @Transactional
    public ApplicationView rename(SessionUser actor, long applicationId, ApplicationInput request) {
        requireTotalAdmin(actor);
        var app = requireApplication(applicationId);
        if (!request.name().equals(app.name)) {
            var detail = "名称从「" + app.name + "」改为「" + request.name() + "」";
            app.name = request.name();
            operationLogs.recordGlobalChange(actor, "修改应用", detail, "APPLICATION", app.id, app.name);
        }
        return new ApplicationView(app.id, app.name);
    }

    @Transactional(readOnly = true)
    public ApplicationPermissions permissionTree(SessionUser actor, long applicationId) {
        requireTotalAdmin(actor);
        var app = requireApplication(applicationId);
        return permissionTree(app, permissions.findByApplicationIdOrderByIdAsc(applicationId));
    }

    static ApplicationPermissions permissionTree(BusinessApplication app, List<Permission> entries) {
        Map<Long, List<PermissionRef>> buttons = new HashMap<>();
        for (var entry : entries) {
            if (entry.parentId != null) {
                buttons.computeIfAbsent(entry.parentId, key -> new ArrayList<>())
                        .add(new PermissionRef(entry.id, entry.name, entry.code));
            }
        }
        var menus = entries.stream().filter(entry -> MENU.equals(entry.type))
                .map(entry -> new MenuNode(entry.id, entry.name, entry.code, buttons.getOrDefault(entry.id, List.of())))
                .toList();
        return new ApplicationPermissions(app.id, app.name, menus);
    }

    @Transactional
    public PermissionView createPermission(SessionUser actor, long applicationId, PermissionCreate request) {
        requireTotalAdmin(actor);
        var app = requireApplication(applicationId);
        var parentId = request.parentId();
        if (MENU.equals(request.type())) {
            if (parentId != null) {
                throw new ManagementFailure(INVALID_REQUEST, "菜单权限的上级必须为空。");
            }
        } else if (parentId == null || !permissions.existsByIdAndApplicationIdAndType(parentId, applicationId, MENU)) {
            throw new ManagementFailure(INVALID_REQUEST, "按钮权限的上级必须是同一应用的菜单。");
        }

        var permission = new Permission();
        permission.applicationId = applicationId;
        permission.parentId = parentId;
        permission.name = request.name();
        permission.code = request.code();
        permission.type = request.type();
        permissions.save(permission);
        operationLogs.recordGlobalChange(actor, "新增权限", "应用「" + app.name + "」，权限标识 " + permission.code,
                "PERMISSION", permission.id, permission.name);
        return view(permission);
    }

    @Transactional
    public PermissionView updatePermission(SessionUser actor, long applicationId, long permissionId,
                                           PermissionUpdate request) {
        requireTotalAdmin(actor);
        var app = requireApplication(applicationId);
        var permission = permissions.findByIdAndApplicationId(permissionId, applicationId)
                .orElseThrow(() -> new ManagementFailure(NOT_FOUND, "该应用下不存在此权限。"));
        if (request.name == null && request.code == null) {
            throw new ManagementFailure(INVALID_REQUEST, "请填写要修改的权限名称或标识。");
        }

        var changes = new ArrayList<String>();
        if (request.name != null && !request.name.equals(permission.name)) {
            changes.add("名称从「" + permission.name + "」改为「" + request.name + "」");
            permission.name = request.name;
        }
        if (request.code != null && !request.code.equals(permission.code)) {
            changes.add("标识从 " + permission.code + " 改为 " + request.code);
            permission.code = request.code;
        }
        if (!changes.isEmpty()) {
            operationLogs.recordGlobalChange(actor, "修改权限", "应用「" + app.name + "」，" + String.join("，", changes),
                    "PERMISSION", permission.id, permission.name);
        }
        return view(permission);
    }

    private BusinessApplication requireApplication(long id) {
        return applications.findById(id)
                .orElseThrow(() -> new ManagementFailure(NOT_FOUND, "应用不存在。"));
    }

    private static PermissionView view(Permission permission) {
        return new PermissionView(permission.id, permission.applicationId, permission.name, permission.code,
                permission.type, permission.parentId);
    }
}
