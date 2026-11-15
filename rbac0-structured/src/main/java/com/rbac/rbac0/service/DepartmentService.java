package com.rbac.rbac0.service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.rbac.rbac0.dto.DepartmentDtos.DepartmentInput;
import com.rbac.rbac0.dto.DepartmentDtos.DepartmentNode;
import com.rbac.rbac0.dto.DepartmentDtos.DepartmentTree;
import com.rbac.rbac0.dto.DepartmentDtos.DepartmentUpdate;
import com.rbac.rbac0.dto.DepartmentDtos.DepartmentView;
import com.rbac.rbac0.dto.SessionDtos.SessionUser;
import com.rbac.rbac0.entity.Department;
import com.rbac.rbac0.mapper.DepartmentMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.rbac.rbac0.service.ManagementFailure.Reason.*;

@Service
public class DepartmentService {

    private final DepartmentMapper departments;
    private final OperationLogService operationLogs;

    public DepartmentService(DepartmentMapper departments, OperationLogService operationLogs) {
        this.departments = departments;
        this.operationLogs = operationLogs;
    }

    @Transactional(readOnly = true)
    public DepartmentTree listTree(SessionUser actor) {
        var visible = managedDepartments(actor, null);
        var children = groupChildren(visible);
        var roots = isTotalAdmin(actor) ? children.getOrDefault(null, List.of()) : List.of(visible.getFirst());
        return new DepartmentTree(roots.stream().map(department -> node(department, children)).toList());
    }

    public List<Department> managedDepartments(SessionUser actor, Long selectedId) {
        requireAdministrator(actor);
        var all = departments.findAllByOrderByIdAsc();
        boolean totalAdmin = isTotalAdmin(actor);
        if (totalAdmin && selectedId == null) {
            return all;
        }

        var byId = all.stream().collect(Collectors.toMap(department -> department.id, Function.identity()));
        var root = byId.get(totalAdmin ? selectedId : actor.departmentId());
        var selected = selectedId == null ? root : byId.get(selectedId);
        if (selectedId != null && selected == null) {
            throw new ManagementFailure(NOT_FOUND, "部门不存在。");
        }
        if (root == null) {
            throw new ManagementFailure(FORBIDDEN, "当前部门管理员没有所属部门。");
        }
        var ancestor = selected;
        while (ancestor != null && !ancestor.id.equals(root.id)) {
            ancestor = byId.get(ancestor.parentId);
        }
        if (ancestor == null) {
            throw new ManagementFailure(OUTSIDE_MANAGEMENT_SCOPE, "请选择本部门或下级部门内的对象。");
        }

        var children = groupChildren(all);
        var visible = new ArrayList<Department>();
        var pending = new ArrayDeque<Department>();
        pending.add(selected);
        while (!pending.isEmpty()) {
            var department = pending.removeFirst();
            visible.add(department);
            pending.addAll(children.getOrDefault(department.id, List.of()));
        }
        return visible;
    }

    public Department requireManagedDepartment(SessionUser actor, long id) {
        return isTotalAdmin(actor) ? requireDepartment(id) : managedDepartments(actor, id).getFirst();
    }

    @Transactional
    public DepartmentView create(SessionUser actor, DepartmentInput request) {
        requireTotalAdmin(actor);
        var parent = request.parentId() == null ? null : requireDepartment(request.parentId());

        var department = new Department();
        department.name = request.name();
        department.parentId = request.parentId();
        departments.save(department);
        operationLogs.recordGlobalChange(actor, "新增部门", "上级为" + parentName(parent),
                "DEPARTMENT", department.id, department.name);
        return new DepartmentView(department.id, department.name, department.parentId);
    }

    @Transactional
    public DepartmentView update(SessionUser actor, long id, DepartmentUpdate request) {
        requireTotalAdmin(actor);
        if (request.name == null && !request.parentProvided()) {
            throw new ManagementFailure(INVALID_REQUEST, "请填写部门名称或上级部门。");
        }

        var department = requireDepartment(id);
        var changes = new ArrayList<String>();
        if (request.name != null && !request.name.equals(department.name)) {
            changes.add("名称从「" + department.name + "」改为「" + request.name + "」");
            department.name = request.name;
        }
        if (request.parentProvided() && !Objects.equals(request.parentId(), department.parentId)) {
            var byId = departments.findAllByOrderByIdAsc().stream()
                    .collect(Collectors.toMap(item -> item.id, Function.identity()));
            var parent = byId.get(request.parentId());
            if (request.parentId() != null && parent == null) {
                throw new ManagementFailure(NOT_FOUND, "上级部门不存在。");
            }
            for (var ancestor = parent; ancestor != null; ancestor = byId.get(ancestor.parentId)) {
                if (ancestor.id.equals(department.id)) {
                    throw new ManagementFailure(INVALID_REQUEST, "上级部门不能是本部门或其下级部门。");
                }
            }
            changes.add("上级从" + parentName(byId.get(department.parentId)) + "改为" + parentName(parent));
            department.parentId = request.parentId();
        }

        if (!changes.isEmpty()) {
            operationLogs.recordGlobalChange(actor, "修改部门", String.join("，", changes),
                    "DEPARTMENT", department.id, department.name);
        }
        return new DepartmentView(department.id, department.name, department.parentId);
    }

    private Department requireDepartment(long id) {
        return departments.findById(id)
                .orElseThrow(() -> new ManagementFailure(NOT_FOUND, "部门不存在。"));
    }

    static boolean isTotalAdmin(SessionUser actor) {
        return "TOTAL_ADMIN".equals(actor.managementRole());
    }

    static void requireAdministrator(SessionUser actor) {
        if (!isTotalAdmin(actor) && !"DEPARTMENT_ADMIN".equals(actor.managementRole())) {
            throw new ManagementFailure(FORBIDDEN, "当前角色无权管理用户和部门。");
        }
    }

    static void requireTotalAdmin(SessionUser actor) {
        if (!isTotalAdmin(actor)) {
            throw new ManagementFailure(FORBIDDEN, "当前操作仅限总管理员。");
        }
    }

    private static Map<Long, List<Department>> groupChildren(List<Department> departments) {
        Map<Long, List<Department>> children = new HashMap<>();
        for (var department : departments) {
            children.computeIfAbsent(department.parentId, key -> new ArrayList<>()).add(department);
        }
        return children;
    }

    private static DepartmentNode node(Department department, Map<Long, List<Department>> children) {
        var descendants = children.getOrDefault(department.id, List.of()).stream()
                .map(child -> node(child, children)).toList();
        return new DepartmentNode(department.id, department.name, department.parentId, descendants);
    }

    private static String parentName(Department department) {
        return department == null ? "顶层" : "「" + department.name + "」";
    }
}
