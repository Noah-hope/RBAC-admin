package com.rbac.rbac1ddd.interfaces;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 接口集成测试：H2 内存库 + 与结构化版本相同的初始权限数据，按 RBAC1 需求的验收条件逐条验证。
 *
 * <p>令牌由测试用私钥直接签发并写入会话，省去登录步骤；应用配置同一对密钥，因此能校验这些令牌。
 */
class RbacDddApiTest extends ManagementApiTestSupport {

  /** 角色列表与创建。 */
  private static final String ROLES = "/api/v1/roles";

  /** 单个角色的查询、修改和删除。 */
  private static final String ROLE = "/api/v1/roles/{roleId}";

  /** 整体替换单个用户的角色。 */
  private static final String USER_ROLES = "/api/v1/users/{userId}/roles";

  /** 批量分配或撤销用户角色。 */
  private static final String USER_ROLE_ASSIGNMENTS = "/api/v1/user-role-assignments";

  long totalAdminRole;
  long departmentAdminRole;
  long app;
  long courseMenu;
  long courseCreate;
  long scoreMenu;
  long roleViewPermission;
  long headquarters;
  long rnd;
  long frontend;
  long finance;
  long admin;
  String adminToken;

  /** 每个测试前准备独立的应用、权限、部门树和已登录的总管理员。 */
  @BeforeEach
  void prepare() {
    totalAdminRole = id("select id from sys_role where role_type='TOTAL_ADMIN'");
    departmentAdminRole = id("select id from sys_role where role_type='DEPARTMENT_ADMIN'");
    roleViewPermission = id("select id from sys_permission where code='role:view'");
    app = insert("sys_application", Map.of("code", uniq("app"), "name", uniq("课程")));
    courseMenu = permission("课程管理", null);
    courseCreate = permission("新增课程", courseMenu);
    scoreMenu = permission("成绩管理", null);
    headquarters = department("总部", null);
    rnd = department("研发部", headquarters);
    frontend = department("前端组", rnd);
    finance = department("财务部", headquarters);
    admin = user("管理员", null, totalAdminRole);
    adminToken = login(admin);
  }

  // ---------- 认证与鉴权 ----------

  @Test
  @DisplayName("RBAC0：没有令牌、没有会话或令牌无效时返回 401")
  void 没有令牌或会话失效时返回401() throws Exception {
    mvc.perform(get(ROLES))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    mvc.perform(
            get(ROLES)
                .header("Authorization", "Bearer " + token(admin, List.of("permission-center"))))
        .andExpect(status().isUnauthorized());
    mvc.perform(get(ROLES).header("Authorization", "Bearer not-a-jwt"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("RBAC0：其他应用的令牌不能调用权限中心接口")
  void 其他应用的令牌不能调用权限中心接口() throws Exception {
    String token = token(admin, List.of("course"));
    session(token, admin);
    mvc.perform(get(ROLES).header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("APPLICATION_MISMATCH"));
  }

  @Test
  @DisplayName("RBAC0：停用账号的令牌失效")
  void 停用账号的令牌失效() throws Exception {
    jdbc.update("update sys_user set status='DISABLED' where id=?", admin);
    call(get(ROLES), adminToken).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("验收3：下一次权限检查立即反映继承关系变化且其他来源的权限保留")
  void 下一次权限检查立即反映继承关系变化且其他来源的权限保留() throws Exception {
    long viewerA = createRole(uniq("查看A"), List.of(roleViewPermission), List.of());
    long viewerB = createRole(uniq("查看B"), List.of(roleViewPermission), List.of());
    long child = createRole(uniq("子"), List.of(), List.of(viewerA, viewerB));
    long member = user("员工", frontend);
    String token = login(member);

    call(get(ROLES), token)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.detail").value("没有该接口的调用权限。"));

    replaceRoles(member, List.of(child)).andExpect(status().isOk());
    // 通过了接口权限检查，被拒绝只是因为普通用户不是管理员
    call(get(ROLES), token).andExpect(jsonPath("$.detail").value("当前角色无权管理用户和部门。"));

    updateRole(child, "子角色", List.of(), List.of(viewerB)).andExpect(status().isOk());
    call(get(ROLES), token).andExpect(jsonPath("$.detail").value("当前角色无权管理用户和部门。"));

    updateRole(child, "子角色", List.of(), List.of()).andExpect(status().isOk());
    call(get(ROLES), token).andExpect(jsonPath("$.detail").value("没有该接口的调用权限。"));
  }

  // ---------- 角色继承 ----------

  @Test
  @DisplayName("验收1、4：多继承多层继承的角色展示直接权限和继承来源")
  void 多继承多层继承的角色展示直接权限和继承来源() throws Exception {
    long grand = createRole(uniq("祖"), List.of(courseMenu), List.of());
    long parent = createRole(uniq("父"), List.of(courseCreate), List.of(grand));
    long other = createRole(uniq("另"), List.of(courseMenu), List.of());
    long child = createRole(uniq("孙"), List.of(scoreMenu), List.of(parent, other));

    call(get(ROLE, child), adminToken)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.directPermissionCount").value(1))
        .andExpect(jsonPath("$.effectivePermissionCount").value(3))
        .andExpect(jsonPath("$.inheritedRoles.length()").value(2))
        .andExpect(
            jsonPath("$.effectivePermissions[?(@.id==" + courseMenu + ")].acquisition")
                .value("INHERITED"))
        .andExpect(
            jsonPath("$.effectivePermissions[?(@.id==" + courseMenu + ")].sourceRoles.length()")
                .value(2))
        .andExpect(
            jsonPath("$.effectivePermissions[?(@.id==" + scoreMenu + ")].acquisition")
                .value("DIRECT"))
        .andExpect(jsonPath("$.applications[0].permissionCount").value(3));
    call(get(ROLE, grand), adminToken)
        .andExpect(jsonPath("$.inheritedByRoles[0].id").value(parent));
  }

  @Test
  @DisplayName("验收2：拒绝自继承、循环继承和管理员角色参与继承且原配置不变")
  void 拒绝自继承循环继承和管理员角色参与继承且原配置不变() throws Exception {
    long parent = createRole(uniq("父"), List.of(courseMenu), List.of());
    long child = createRole(uniq("子"), List.of(), List.of(parent));

    updateRole(parent, "父", List.of(courseMenu), List.of(child))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value("不能继承自身或形成循环继承。"));
    updateRole(parent, "父", List.of(courseMenu), List.of(parent))
        .andExpect(jsonPath("$.detail").value("不能继承自身或形成循环继承。"));
    updateRole(child, "子", List.of(), List.of(totalAdminRole))
        .andExpect(jsonPath("$.detail").value("父角色必须是当前存在的业务角色。"));
    call(
            put(ROLE, totalAdminRole)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    body(
                        Map.of(
                            "name", "x", "permissionIds", List.of(), "inheritedRoleIds", List.of()))),
            adminToken)
        .andExpect(status().isForbidden());

    call(get(ROLE, parent), adminToken)
        .andExpect(jsonPath("$.inheritedRoles.length()").value(0));
    assertThat(
            count(
                "select count(*) from sys_operation_log where result='FAILURE' and detail like ?",
                "PUT " + ROLE + "%"))
        .isGreaterThanOrEqualTo(3);
  }

  @Test
  @DisplayName("验收5：创建角色时校验按钮菜单和重复权限")
  void 创建角色时校验按钮菜单和重复权限() throws Exception {
    long parent = createRole(uniq("父"), List.of(courseMenu), List.of());
    String name = uniq("重名");
    createRole(name, List.of(scoreMenu), List.of());

    postRole(uniq("缺菜单"), List.of(courseCreate), List.of())
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.detail")
                .value(org.hamcrest.Matchers.containsString("缺少所属菜单「课程管理」")));
    postRole(uniq("重复"), List.of(courseMenu), List.of(parent))
        .andExpect(jsonPath("$.detail").value("权限「课程管理」已通过继承获得，不能新增重复直接配置。"));
    postRole(name, List.of(), List.of())
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail").value("该角色名称已存在。"));
    postRole(uniq("按钮"), List.of(courseCreate), List.of(parent)).andExpect(status().isCreated());
    call(
            post(ROLES).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"x\"}"),
            adminToken)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
  }

  @Test
  @DisplayName("RBAC0：修改角色写入操作记录")
  void 修改角色写入操作记录() throws Exception {
    long parent = createRole(uniq("父"), List.of(courseMenu), List.of());
    long role = createRole(uniq("改"), List.of(scoreMenu), List.of());
    String newName = uniq("新名");

    updateRole(role, newName, List.of(), List.of(parent))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value(newName))
        .andExpect(jsonPath("$.permissionIds.length()").value(0));

    String detail =
        jdbc.queryForObject(
            "select l.detail from sys_operation_log l join sys_operation_log_target t on t.operation_log_id=l.id"
                + " where l.action='修改角色' and t.target_id=?",
            String.class,
            role);
    assertThat(detail)
        .contains("名称从")
        .contains("直接权限从 [" + scoreMenu + "] 改为 []")
        .contains("继承角色从 无 改为");
  }

  @Test
  @DisplayName("RBAC0：角色列表支持关键字、类型筛选和分页")
  void 角色列表支持关键字类型和分页() throws Exception {
    String keyword = uniq("列表");
    createRole(keyword + "1", List.of(), List.of());
    createRole(keyword + "2", List.of(), List.of());

    call(get(ROLES).param("keyword", keyword).param("pageSize", "1"), adminToken)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(2))
        .andExpect(jsonPath("$.items.length()").value(1));
    call(get(ROLES).param("roleType", "TOTAL_ADMIN"), adminToken)
        .andExpect(jsonPath("$.items[0].roleType").value("TOTAL_ADMIN"));
    call(get(ROLES).param("pageSize", "101"), adminToken).andExpect(status().isBadRequest());
  }

  // ---------- 部门管理员 ----------

  @Test
  @DisplayName("RBAC0：部门管理员只能管理本部门及下级部门且只能查询业务角色")
  void 部门管理员只能管理本部门及下级部门且只能查询业务角色() throws Exception {
    long manager = user("部门管理员", rnd, departmentAdminRole);
    String token = login(manager);
    long role = createRole(uniq("业务"), List.of(), List.of());
    long inScope = user("前端", frontend);
    long outside = user("财务", finance);

    call(
            put(USER_ROLES, inScope)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("roleIds", List.of(role)))),
            token)
        .andExpect(status().isOk());
    call(
            put(USER_ROLES, outside)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("roleIds", List.of(role)))),
            token)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("OUTSIDE_MANAGEMENT_SCOPE"));
    call(get(ROLES).param("roleType", "TOTAL_ADMIN"), token)
        .andExpect(jsonPath("$.detail").value("部门管理员只能查询业务角色。"));
    call(get(ROLE, totalAdminRole), token)
        .andExpect(jsonPath("$.detail").value("部门管理员只能查询业务角色。"));
    call(get(ROLES).param("keyword", "部门管理员"), token)
        .andExpect(jsonPath("$.total").value(0));
    call(
            post(ROLES)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    body(
                        Map.of(
                            "name",
                            uniq("x"),
                            "permissionIds",
                            List.of(),
                            "inheritedRoleIds",
                            List.of()))),
            token)
        .andExpect(status().isForbidden());
  }

  // ---------- 用户角色关联 ----------

  @Test
  @DisplayName("RBAC0：更新用户角色列表只变更差异且空列表解除全部业务角色")
  void 更新用户角色列表只变更差异且空列表解除全部业务角色() throws Exception {
    long a = createRole(uniq("甲"), List.of(), List.of());
    long b = createRole(uniq("乙"), List.of(), List.of());
    long member = user("员工", frontend, departmentAdminRole);

    replaceRoles(member, List.of(a)).andExpect(jsonPath("$.roleIds[0]").value(a));
    replaceRoles(member, List.of(a, b)).andExpect(status().isOk());
    assertThat(roleIdsOf(member)).containsExactlyInAnyOrder(a, b, departmentAdminRole);

    replaceRoles(member, List.of()).andExpect(status().isOk());
    assertThat(roleIdsOf(member)).containsExactly(departmentAdminRole);

    replaceRoles(member, List.of(totalAdminRole))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value("这里只能分配或撤销业务角色。"));
    replaceRoles(member, List.of(999_999L)).andExpect(status().isNotFound());
    call(
            put(USER_ROLES, 999_999L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("roleIds", List.of()))),
            adminToken)
        .andExpect(status().isNotFound());
    assertThat(
            count(
                "select count(*) from sys_operation_log_target t join sys_operation_log l on l.id=t.operation_log_id"
                    + " where l.action='修改用户授权' and t.target_id=? and t.department_id_at_event=?",
                member,
                frontend))
        .isEqualTo(3);
  }

  @Test
  @DisplayName("RBAC0：批量分配和撤销角色只统计实际变化的用户")
  void 批量分配和撤销角色() throws Exception {
    long role = createRole(uniq("批量"), List.of(), List.of());
    long first = user("一", frontend);
    long second = user("二", finance, role);

    batch("GRANT", List.of(first, second), List.of(role))
        .andExpect(jsonPath("$.action").value("GRANT"))
        .andExpect(jsonPath("$.affectedUsers").value(1));
    batch("REVOKE", List.of(first, second), List.of(role))
        .andExpect(jsonPath("$.affectedUsers").value(2));
    batch("REVOKE", List.of(first), List.of(role)).andExpect(jsonPath("$.affectedUsers").value(0));
    assertThat(roleIdsOf(first)).isEmpty();
  }

  @Test
  @DisplayName("验收10：授权变化后待审核权限申请自动变为已满足")
  void 授权变化后待审核权限申请自动变为已满足() throws Exception {
    long member = user("申请人", frontend);
    long parent = createRole(uniq("父"), List.of(), List.of());
    long child = createRole(uniq("子"), List.of(), List.of(parent));
    replaceRoles(member, List.of(child));
    long request =
        insert(
            "sys_permission_request",
            Map.of(
                "applicant_user_id", member,
                "permission_id", scoreMenu,
                "reason", "工作需要",
                "status", "PENDING",
                "created_at", LocalDateTime.now()));

    updateRole(parent, "父", List.of(scoreMenu), List.of()).andExpect(status().isOk());

    assertThat(
            jdbc.queryForObject(
                "select status from sys_permission_request where id=?", String.class, request))
        .isEqualTo("SATISFIED");
  }

  // ---------- 角色删除 ----------

  @Test
  @DisplayName("验收7：删除角色时检查用户关联和继承关系并保留权限定义")
  void 删除角色时检查用户关联和继承关系并保留权限定义() throws Exception {
    long parent = createRole(uniq("父"), List.of(courseMenu), List.of());
    createRole(uniq("子"), List.of(), List.of(parent));
    long held = createRole(uniq("持有"), List.of(), List.of());
    user("员工", frontend, held);
    long free = createRole(uniq("空闲"), List.of(courseMenu, courseCreate), List.of());

    call(delete(ROLE, parent), adminToken)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail").value("该角色仍存在父角色或子角色，请先解除继承关系。"));
    call(delete(ROLE, held), adminToken)
        .andExpect(jsonPath("$.detail").value("该角色仍关联 1 个用户，请先撤销用户关联。"));
    call(delete(ROLE, departmentAdminRole), adminToken).andExpect(status().isForbidden());
    call(delete(ROLE, 999_999L), adminToken).andExpect(status().isNotFound());

    call(delete(ROLE, free), adminToken).andExpect(status().isNoContent());
    assertThat(count("select count(*) from sys_role where id=?", free)).isZero();
    assertThat(count("select count(*) from sys_role_permission where role_id=?", free)).isZero();
    assertThat(
            count(
                "select count(*) from sys_permission where id in (?, ?)", courseMenu, courseCreate))
        .isEqualTo(2);
  }

  // ---------- 辅助方法 ----------

  /** 以总管理员身份提交创建角色请求，说明固定为空字符串，不断言结果。 */
  private ResultActions postRole(String name, List<Long> permissions, List<Long> parents)
      throws Exception {
    return call(
        post(ROLES)
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                body(
                    Map.of(
                        "name",
                        name,
                        "description",
                        "",
                        "permissionIds",
                        permissions,
                        "inheritedRoleIds",
                        parents))),
        adminToken);
  }

  /** 创建角色并断言返回 201，返回新角色 ID。 */
  private long createRole(String name, List<Long> permissions, List<Long> parents)
      throws Exception {
    MvcResult response =
        postRole(name, permissions, parents).andExpect(status().isCreated()).andReturn();
    return json.readTree(response.getResponse().getContentAsString()).get("id").asLong();
  }

  /** 以总管理员身份整体修改角色配置，说明固定为空字符串，不断言结果。 */
  private ResultActions updateRole(
      long id, String name, List<Long> permissions, List<Long> parents) throws Exception {
    return call(
        put(ROLE, id)
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                body(
                    Map.of(
                        "name",
                        name,
                        "description",
                        "",
                        "permissionIds",
                        permissions,
                        "inheritedRoleIds",
                        parents))),
        adminToken);
  }

  /** 以总管理员身份整体替换用户的角色列表。 */
  private ResultActions replaceRoles(long userId, List<Long> roleIds) throws Exception {
    return call(
        put(USER_ROLES, userId)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body(Map.of("roleIds", roleIds))),
        adminToken);
  }

  /** 以总管理员身份批量分配（GRANT）或撤销（REVOKE）用户角色。 */
  private ResultActions batch(String action, List<Long> userIds, List<Long> roleIds)
      throws Exception {
    return call(
        patch(USER_ROLE_ASSIGNMENTS)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body(Map.of("action", action, "userIds", userIds, "roleIds", roleIds))),
        adminToken);
  }

  /** 直接从数据库读取用户当前持有的全部角色 ID，包括管理员角色。 */
  private List<Long> roleIdsOf(long userId) {
    return jdbc.queryForList(
        "select role_id from sys_user_role where user_id=?", Long.class, userId);
  }

  /** 在当前测试应用下插入权限：没有父级时是菜单，有父级时是该菜单下的按钮。 */
  private long permission(String name, Long parent) {
    Map<String, Object> values =
        new HashMap<>(
            Map.of(
                "application_id",
                app,
                "name",
                name,
                "code",
                uniq("p"),
                "type",
                parent == null ? "MENU" : "BUTTON"));
    if (parent != null) {
      values.put("parent_id", parent);
    }
    return insert("sys_permission", values);
  }
}
