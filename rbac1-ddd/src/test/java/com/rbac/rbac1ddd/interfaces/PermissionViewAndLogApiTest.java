package com.rbac.rbac1ddd.interfaces;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 接口集成测试：「我的权限」、角色配置权限目录和操作记录查询。
 *
 * <p>角色、权限和操作记录直接写入数据库，只验证三个只读接口的结果与 rbac1-structured 相同。
 */
class PermissionViewAndLogApiTest extends ManagementApiTestSupport {

  /** 当前用户的权限。 */
  private static final String MY_PERMISSIONS = "/api/v1/me/permissions";

  /** 角色配置权限目录。 */
  private static final String PERMISSION_CATALOG = "/api/v1/permission-catalog";

  /** 操作记录查询。 */
  private static final String OPERATION_LOGS = "/api/v1/operation-logs";

  long totalAdminRole;
  long departmentAdminRole;
  long app;
  long admin;
  String adminToken;

  /** 每个测试前准备独立的业务应用和已登录的总管理员。 */
  @BeforeEach
  void prepare() {
    totalAdminRole = id("select id from sys_role where role_type='TOTAL_ADMIN'");
    departmentAdminRole = id("select id from sys_role where role_type='DEPARTMENT_ADMIN'");
    app = insert("sys_application", Map.of("code", uniq("app"), "name", uniq("课程")));
    admin = user("管理员", null, totalAdminRole);
    adminToken = login(admin);
  }

  // ---------- 我的权限 ----------

  @Test
  @DisplayName("验收4：我的权限按应用列出菜单和按钮，来源为直接配置的角色，中间传递角色不计为来源")
  void 我的权限列出实际配置来源() throws Exception {
    long menu = permission(app, "课程管理", null);
    long button = permission(app, "审核课程", menu);
    permission(app, "未获得菜单", null);
    long emptyApp = insert("sys_application", Map.of("code", uniq("app"), "name", uniq("空应用")));
    long parent = role(uniq("父"), List.of(menu), List.of());
    long middle = role(uniq("中"), List.of(button), List.of(parent));
    long child = role(uniq("子"), List.of(), List.of(middle));
    long copy = role(uniq("副本"), List.of(menu), List.of());
    long member = user("员工", null, child, copy);

    JsonNode mine = read(call(get(MY_PERMISSIONS), login(member)));
    assertThat(mine.path("businessRoles").findValuesAsText("id"))
        .containsExactly(Long.toString(child), Long.toString(copy));
    JsonNode course = find(mine.path("applications"), "applicationId", app);
    assertThat(course.path("applicationName").asText()).startsWith("课程");
    assertThat(course.path("applicationCode").asText()).startsWith("app");
    assertThat(course.path("menus")).hasSize(1);
    JsonNode courseMenu = course.path("menus").get(0);
    assertThat(courseMenu.path("id").asLong()).isEqualTo(menu);
    assertThat(courseMenu.path("name").asText()).isEqualTo("课程管理");
    assertThat(ids(courseMenu.path("sourceRoles"))).containsExactly(parent, copy);
    assertThat(courseMenu.path("buttons")).hasSize(1);
    JsonNode review = courseMenu.path("buttons").get(0);
    assertThat(review.path("id").asLong()).isEqualTo(button);
    assertThat(review.path("name").asText()).isEqualTo("审核课程");
    assertThat(review.path("code").asText()).startsWith("p");
    assertThat(ids(review.path("sourceRoles"))).containsExactly(middle);
    assertThat(find(mine.path("applications"), "applicationId", emptyApp).path("menus")).isEmpty();
  }

  @Test
  @DisplayName("RBAC0：我的权限只列出业务角色，预置管理权限以管理角色为来源")
  void 我的权限不把管理角色列为业务角色() throws Exception {
    long headquarters = department(uniq("总部"), null);
    long manager = user("部门管理员", headquarters, departmentAdminRole);

    call(get(MY_PERMISSIONS), login(manager))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.businessRoles.length()").value(0))
        .andExpect(jsonPath("$.applications[0].applicationCode").value("permission-center"))
        .andExpect(jsonPath("$.applications[0].menus[0].sourceRoles[0].id")
            .value(departmentAdminRole))
        .andExpect(jsonPath("$.applications[0].menus[*].code").value(
            Matchers.not(Matchers.hasItem("application:view"))));
  }

  // ---------- 权限目录 ----------

  @Test
  @DisplayName("RBAC0：总管理员和部门管理员都能读取按应用分组的完整权限目录")
  void 管理员读取完整权限目录() throws Exception {
    long menu = permission(app, "课程管理", null);
    long button = permission(app, "新增课程", menu);
    long manager = user("部门管理员", department(uniq("总部"), null), departmentAdminRole);

    for (String token : List.of(adminToken, login(manager))) {
      JsonNode catalog = read(call(get(PERMISSION_CATALOG), token));
      assertThat(catalog.path("applications").get(0).path("code").asText())
          .isEqualTo("permission-center");
      JsonNode course = find(catalog.path("applications"), "id", app);
      assertThat(course.path("name").asText()).startsWith("课程");
      assertThat(course.path("permissions")).hasSize(2);
      JsonNode first = course.path("permissions").get(0);
      assertThat(first.path("id").asLong()).isEqualTo(menu);
      assertThat(first.path("applicationId").asLong()).isEqualTo(app);
      assertThat(first.path("name").asText()).isEqualTo("课程管理");
      assertThat(first.path("type").asText()).isEqualTo("MENU");
      assertThat(first.has("parentId")).isTrue();
      assertThat(first.path("parentId").isNull()).isTrue();
      JsonNode second = course.path("permissions").get(1);
      assertThat(second.path("id").asLong()).isEqualTo(button);
      assertThat(second.path("type").asText()).isEqualTo("BUTTON");
      assertThat(second.path("parentId").asLong()).isEqualTo(menu);
    }
  }

  @Test
  @DisplayName("RBAC0：拥有角色查看权限的普通用户也不能读取权限目录")
  void 普通用户不能读取权限目录() throws Exception {
    long roleView = id("select id from sys_permission where code='role:view'");
    long member = user("员工", null, role(uniq("查看"), List.of(roleView), List.of()));

    call(get(PERMISSION_CATALOG), login(member))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.detail").value("当前角色无权管理用户和部门。"));
  }

  // ---------- 操作记录 ----------

  @Test
  @DisplayName("RBAC0：部门管理员只看到操作时在管理范围内的用户记录，批量记录只列出范围内的对象")
  void 部门管理员按操作时的部门查看记录() throws Exception {
    long headquarters = department(uniq("总部"), null);
    long branch = department(uniq("分部"), headquarters);
    long other = department(uniq("其他"), null);
    String marker = uniq("记录");
    String outsider = uniq("范围外");
    long batch =
        log("批量分配角色", "USER_SCOPE", marker, at(5),
            target(uniq("甲"), branch), target(outsider, other), target("丙", headquarters));
    log("分配角色", "USER_SCOPE", marker, at(6), target(uniq("丁"), other));
    log("新增部门", "GLOBAL", marker, at(7), target(uniq("戊"), headquarters));
    long older = log("停用账号", "USER_SCOPE", marker, at(4), target(uniq("己"), branch));
    String manager = login(user("部门管理员", headquarters, departmentAdminRole));

    call(get(OPERATION_LOGS), manager)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(2))
        .andExpect(jsonPath("$.page").value(1))
        .andExpect(jsonPath("$.pageSize").value(20))
        .andExpect(jsonPath("$.items[0].id").value(batch))
        .andExpect(jsonPath("$.items[0].occurredAt").value("2026-01-01T08:05:00Z"))
        .andExpect(jsonPath("$.items[0].actorName").value("记录人"))
        .andExpect(jsonPath("$.items[0].action").value("批量分配角色"))
        .andExpect(jsonPath("$.items[0].detail").value(marker))
        .andExpect(jsonPath("$.items[0].result").value("SUCCESS"))
        .andExpect(jsonPath("$.items[0].targetNames.length()").value(2))
        .andExpect(jsonPath("$.items[0].targetNames[1]").value("丙"))
        .andExpect(jsonPath("$.items[1].id").value(older));
    call(get(OPERATION_LOGS).param("keyword", outsider), manager)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(0))
        .andExpect(jsonPath("$.items.length()").value(0));
    call(get(OPERATION_LOGS).param("keyword", "丙"), manager)
        .andExpect(jsonPath("$.total").value(1))
        .andExpect(jsonPath("$.items[0].id").value(batch));
    call(get(OPERATION_LOGS).param("page", "2").param("pageSize", "1"), manager)
        .andExpect(jsonPath("$.total").value(2))
        .andExpect(jsonPath("$.items[0].id").value(older));
  }

  @Test
  @DisplayName("RBAC0：总管理员看到全部记录和全部对象，按时间倒序并支持搜索对象名称")
  void 总管理员查看全部记录() throws Exception {
    String marker = uniq("全局");
    String outsider = uniq("对象");
    long global = log("登记应用", "GLOBAL", marker, at(9), target("课程系统", null));
    long batch =
        log("批量撤销角色", "USER_SCOPE", marker, at(8),
            target("甲", department(uniq("一"), null)), target(outsider, null));
    long failure = log("操作失败", "GLOBAL", marker, at(9), target("/api/v1/roles", null));
    jdbc.update("update sys_operation_log set result='FAILURE' where id=?", failure);

    call(get(OPERATION_LOGS).param("keyword", marker), adminToken)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(3))
        .andExpect(jsonPath("$.items[0].id").value(failure))
        .andExpect(jsonPath("$.items[0].result").value("FAILURE"))
        .andExpect(jsonPath("$.items[1].id").value(global))
        .andExpect(jsonPath("$.items[2].id").value(batch))
        .andExpect(jsonPath("$.items[2].targetNames[*]").value(Matchers.contains("甲", outsider)));
    call(get(OPERATION_LOGS).param("keyword", outsider), adminToken)
        .andExpect(jsonPath("$.total").value(1))
        .andExpect(jsonPath("$.items[0].id").value(batch));
  }

  @Test
  @DisplayName("RBAC0：普通用户和没有所属部门的部门管理员不能查看操作记录")
  void 非管理员不能查看操作记录() throws Exception {
    long auditView = id("select id from sys_permission where code='audit:view'");
    long member = user("员工", null, role(uniq("审计"), List.of(auditView), List.of()));
    long homeless = user("部门管理员", null, departmentAdminRole);

    call(get(OPERATION_LOGS), login(user("员工", null)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.detail").value("没有该接口的调用权限。"));
    call(get(OPERATION_LOGS), login(member))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.detail").value("当前角色无权管理用户和部门。"));
    call(get(OPERATION_LOGS), login(homeless))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.detail").value("当前部门管理员没有所属部门。"));
  }

  @Test
  @DisplayName("RBAC0：操作记录的分页参数和关键字长度超出范围时返回 400")
  void 操作记录查询参数超出范围() throws Exception {
    call(get(OPERATION_LOGS).param("pageSize", "101"), adminToken)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    call(get(OPERATION_LOGS).param("page", "0"), adminToken)
        .andExpect(status().isBadRequest());
    call(get(OPERATION_LOGS).param("keyword", "字".repeat(101)), adminToken)
        .andExpect(status().isBadRequest());
  }

  /** 要求响应为 200 并解析响应体。 */
  private JsonNode read(ResultActions response) throws Exception {
    String content =
        response.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    return json.readTree(content);
  }

  /** 在数组中找出指定字段等于给定 ID 的唯一元素。 */
  private static JsonNode find(JsonNode array, String field, long id) {
    List<JsonNode> matches = new ArrayList<>();
    array.forEach(
        node -> {
          if (node.path(field).asLong() == id) {
            matches.add(node);
          }
        });
    assertThat(matches).hasSize(1);
    return matches.get(0);
  }

  /** 角色引用数组中的角色 ID，保持原有顺序。 */
  private static List<Long> ids(JsonNode refs) {
    List<Long> result = new ArrayList<>();
    refs.forEach(ref -> result.add(ref.path("id").asLong()));
    return result;
  }

  /** 在应用下插入菜单（parent 为 null）或按钮，返回权限 ID。 */
  private long permission(long applicationId, String name, Long parent) {
    Map<String, Object> values =
        new HashMap<>(
            Map.of(
                "application_id",
                applicationId,
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

  /** 插入业务角色及其直接权限和父角色，返回角色 ID。 */
  private long role(String name, List<Long> permissions, List<Long> parents) {
    long id =
        insert("sys_role", Map.of("name", name, "description", "", "role_type", "BUSINESS"));
    for (long permission : permissions) {
      jdbc.update(
          "insert into sys_role_permission(role_id, permission_id) values (?, ?)", id, permission);
    }
    for (long parent : parents) {
      jdbc.update(
          "insert into sys_role_inheritance(role_id, inherited_role_id) values (?, ?)", id, parent);
    }
    return id;
  }

  /** 2026-01-01 08:00 UTC 之后若干分钟的时刻。 */
  private static LocalDateTime at(int minutes) {
    return LocalDateTime.of(2026, 1, 1, 8, minutes);
  }

  /**
   * 操作对象快照。
   *
   * @param name 对象名称
   * @param departmentId 操作时对象所在部门，非用户对象为 null
   */
  private record Target(String name, Long departmentId) {}

  /** 构造操作对象快照。 */
  private static Target target(String name, Long departmentId) {
    return new Target(name, departmentId);
  }

  /** 插入一条由「记录人」完成的成功操作记录及其对象，对象按参数顺序编号，返回记录 ID。 */
  private long log(
      String action, String scope, String detail, LocalDateTime occurredAt, Target... targets) {
    long id =
        insert(
            "sys_operation_log",
            Map.of(
                "occurred_at",
                occurredAt,
                "actor_user_id",
                admin,
                "actor_name",
                "记录人",
                "action",
                action,
                "scope",
                scope,
                "detail",
                detail,
                "result",
                "SUCCESS"));
    for (int i = 0; i < targets.length; i++) {
      jdbc.update(
          "insert into sys_operation_log_target(operation_log_id, target_no, target_type,"
              + " target_name, department_id_at_event) values (?, ?, 'USER', ?, ?)",
          id,
          i + 1,
          targets[i].name(),
          targets[i].departmentId());
    }
    return id;
  }
}
