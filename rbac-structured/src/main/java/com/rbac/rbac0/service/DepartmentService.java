package com.rbac.rbac0.service;

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

import static com.rbac.rbac0.service.DepartmentFailure.Reason.*;

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
        boolean totalAdmin = "TOTAL_ADMIN".equals(actor.managementRole());
        if (!totalAdmin && !"DEPARTMENT_ADMIN".equals(actor.managementRole())) {
            throw new DepartmentFailure(FORBIDDEN, "当前角色无权查看部门结构。");
        }

        Map<Long, List<Department>> children = new HashMap<>();
        Department managedRoot = null;
        for (var department : departments.findAllByOrderByIdAsc()) {
            children.computeIfAbsent(department.parentId, key -> new ArrayList<>()).add(department);
            if (department.id.equals(actor.departmentId())) {
                managedRoot = department;
            }
        }

        if (totalAdmin) {
            return new DepartmentTree(children.getOrDefault(null, List.of()).stream()
                    .map(department -> node(department, children)).toList());
        }
        if (managedRoot == null) {
            throw new DepartmentFailure(FORBIDDEN, "当前部门管理员没有所属部门。");
        }
        return new DepartmentTree(List.of(node(managedRoot, children)));
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
            throw new DepartmentFailure(INVALID_REQUEST, "请填写部门名称或上级部门。");
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
                throw new DepartmentFailure(NOT_FOUND, "上级部门不存在。");
            }
            for (var ancestor = parent; ancestor != null; ancestor = byId.get(ancestor.parentId)) {
                if (ancestor.id.equals(department.id)) {
                    throw new DepartmentFailure(INVALID_REQUEST, "上级部门不能是本部门或其下级部门。");
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
                .orElseThrow(() -> new DepartmentFailure(NOT_FOUND, "部门不存在。"));
    }

    private static void requireTotalAdmin(SessionUser actor) {
        if (!"TOTAL_ADMIN".equals(actor.managementRole())) {
            throw new DepartmentFailure(FORBIDDEN, "只有总管理员可以维护部门结构。");
        }
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
