package com.rbac.rbac1ddd.interfaces;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 接口集成测试：权限申请和角色创建申请。
 *
 * <p>对应 rbac1-structured 的 RequestWorkflowTest、InheritedPermissionRequestsTest 和
 * InheritedRoleCreationRequestsTest，通过 HTTP 验证同样的规则、状态码、错误码和中文提示。
 */
class RequestApiTest extends ManagementApiTestSupport {

  /** 本人可申请的权限目录。 */
  private static final String REQUESTABLE = "/api/v1/me/requestable-permissions";

  /** 权限申请列表与提交。 */
  private static final String PERMISSION_REQUESTS = "/api/v1/permission-requests";

  /** 单个权限申请详情。 */
  private static final String PERMISSION_REQUEST = "/api/v1/permission-requests/{requestId}";

  /** 撤回权限申请。 */
  private static final String WITHDRAWALS = "/api/v1/permission-requests/{requestId}/withdrawals";

  /** 审核权限申请。 */
  private static final String REVIEWS = "/api/v1/permission-requests/{requestId}/reviews";

  /** 角色创建申请列表与提交。 */
  private static final String ROLE_REQUESTS = "/api/v1/role-creation-requests";

  /** 单个角色创建申请详情。 */
  private static final String ROLE_REQUEST = "/api/v1/role-creation-requests/{requestId}";

  /** 审核角色创建申请。 */
  private static final String ROLE_REVIEWS = "/api/v1/role-creation-requests/{requestId}/reviews";

  /** 单个角色的查询和修改。 */
  private static final String ROLE = "/api/v1/roles/{roleId}";

  /** 整体替换单个用户的业务角色。 */
  private static final String USER_ROLES = "/api/v1/users/{userId}/roles";

  /** 操作记录查询。 */
  private static final String OPERATION_LOGS = "/api/v1/operation-logs";

  long departmentAdminRole;
  long app;
  long menu;
  long button;
  String adminToken;

  /** 每个测试前准备独立的业务应用（菜单 + 按钮）和已登录的总管理员。 */
  @BeforeEach
  void prepare() {
    long totalAdminRole = id("select id from sys_role where role_type='TOTAL_ADMIN'");
    departmentAdminRole = id("select id from sys_role where role_type='DEPARTMENT_ADMIN'");
    app = insert("sys_application", Map.of("code", uniq("app"), "name", uniq("课程")));
    menu = permission(app, "课程管理", null);
    button = permission(app, "审核课程", menu);
    adminToken = login(user("总管理员", null, totalAdminRole));
  }

  // ---------- 权限申请 ----------

  @Test
  @DisplayName("验收9：菜单通过继承获得后目录只列出其下未获得的按钮，可以申请按钮，不能重复申请已继承的菜单")
  void 继承菜单后申请其下按钮() throws Exception {
    long base = role(List.of(menu), List.of());
    long child = role(List.of(), List.of(base));
    String teacher = login(user("教师", null, child));

    JsonNode catalog = send(get(REQUESTABLE), teacher, null, 200);
    JsonNode course = find(catalog.path("applications"), "id", app);
    assertThat(course.path("groups")).hasSize(1);
    assertThat(course.path("groups").get(0).path("menu").path("id").asLong()).isEqualTo(menu);
    assertThat(ids(course.path("groups").get(0).path("permissions"))).containsExactly(button);

    JsonNode submitted = submit(teacher, button, "  审核课程  ", 201);
    assertThat(submitted.path("status").asText()).isEqualTo("PENDING");
    assertThat(submitted.path("reason").asText()).isEqualTo("审核课程");
    assertThat(submitted.path("permission").path("type").asText()).isEqualTo("BUTTON");
    assertThat(submitted.has("approvalRoles")).isFalse();
    assertProblem(submit(teacher, button, "重复", 409), "CONFLICT", "已拥有、已有待审核申请，或尚未获得所属菜单。");
    assertProblem(submit(teacher, menu, "已继承", 409), "CONFLICT", "已拥有、已有待审核申请，或尚未获得所属菜单。");
    assertProblem(submit(teacher, 9_000_000L, "不存在", 404), "NOT_FOUND", "权限不存在。");
    long departmentMenu =
        id(
            "select p.id from sys_permission p join sys_application a on a.id=p.application_id"
                + " where a.code='permission-center' and p.code='application:view'");
    assertProblem(
        submit(teacher, departmentMenu, "管理职责", 403),
        "FORBIDDEN",
        "该权限属于固定管理员职责，不能通过业务角色申请。");
    send(post(PERMISSION_REQUESTS), teacher, Map.of("permissionId", button, "reason", " "), 400);
  }

  @Test
  @DisplayName("验收9：候选角色按全部应用的继承权限评估，审批只给申请人分配所选角色并留下操作记录")
  void 审批按继承权限评估候选角色() throws Exception {
    long otherApp = insert("sys_application", Map.of("code", uniq("app"), "name", uniq("其他")));
    long extra = permission(otherApp, "其他菜单", null);
    long base = role(List.of(menu), List.of());
    long reviewer = role(List.of(button), List.of(base));
    long minimal = role(List.of(), List.of(reviewer));
    long excessive = role(List.of(extra), List.of(reviewer));
    long teacher = user("教师", null, base);
    String token = login(teacher);
    long request = submit(token, button, "审核课程", 201).path("id").asLong();

    assertThat(send(get(PERMISSION_REQUEST, request), token, null, 200).has("approvalRoles"))
        .isFalse();
    JsonNode options =
        send(get(PERMISSION_REQUEST, request), adminToken, null, 200).path("approvalRoles");
    JsonNode good = findOption(options, minimal);
    assertThat(good.path("selectable").asBoolean()).isTrue();
    assertThat(good.path("role").path("effectivePermissions")).hasSize(2);
    JsonNode bad = findOption(options, excessive);
    assertThat(bad.path("selectable").asBoolean()).isFalse();
    assertThat(bad.path("extraPermissionIds").get(0).asLong()).isEqualTo(extra);

    assertProblem(
        review(request, Map.of("decision", "APPROVE", "roleId", excessive), 400),
        "INVALID_REQUEST",
        "该角色附带无关权限，请先配置最小权限角色。");
    assertProblem(
        review(request, Map.of("decision", "APPROVE", "roleId", base), 400),
        "INVALID_REQUEST",
        "请选择包含申请权限的业务角色。");
    JsonNode approved = review(request, Map.of("decision", "APPROVE", "roleId", minimal), 200);
    assertThat(approved.path("status").asText()).isEqualTo("APPROVED");
    assertThat(approved.path("approvedRoleId").asLong()).isEqualTo(minimal);
    assertThat(approved.path("reviewedAt").isTextual()).isTrue();
    assertThat(
            jdbc.queryForList(
                "select role_id from sys_user_role where user_id=? order by role_id",
                Long.class,
                teacher))
        .containsExactly(base, minimal);
    assertProblem(review(request, Map.of("decision", "REJECT"), 409), "CONFLICT", "申请已处理。");
    JsonNode logs =
        send(get(OPERATION_LOGS).param("keyword", "权限申请#" + request), adminToken, null, 200);
    assertThat(logs.path("items").findValuesAsText("action"))
        .contains("提交权限申请", "审核权限申请");
  }

  @Test
  @DisplayName("验收9：审批时按最新继承配置重新校验候选角色，失败时不分配角色且申请保持待审核")
  void 审批按最新配置重新校验() throws Exception {
    long otherApp = insert("sys_application", Map.of("code", uniq("app"), "name", uniq("其他")));
    long extra = permission(otherApp, "其他菜单", null);
    long base = role(List.of(menu), List.of());
    long source = role(List.of(button), List.of(base));
    long candidate = role(List.of(), List.of(source));
    long teacher = user("教师", null, base);
    long request = submit(login(teacher), button, "待审核", 201).path("id").asLong();

    send(
        put(ROLE, source),
        adminToken,
        configuration(uniq("来源"), List.of(button, extra), List.of(base)),
        200);

    review(request, Map.of("decision", "APPROVE", "roleId", candidate), 400);
    assertThat(count("select count(*) from sys_user_role where user_id=? and role_id=?", teacher, candidate))
        .isZero();
    assertThat(requestStatus(request)).isEqualTo("PENDING");
  }

  @Test
  @DisplayName("验收10：父角色获得权限后受影响用户的待审核申请转为已满足，撤回和无关申请保持原状态")
  void 父角色授权后结算待审核申请() throws Exception {
    long other = permission(app, "删除课程", menu);
    long parent = role(List.of(menu), List.of());
    long child = role(List.of(), List.of(parent));
    String teacher = login(user("教师", null, child));
    long withdrawn = submit(teacher, button, "已撤回", 201).path("id").asLong();
    send(post(WITHDRAWALS, withdrawn), teacher, null, 200);
    long pending = submit(teacher, button, "待满足", 201).path("id").asLong();
    long unmatched = submit(teacher, other, "仍待审", 201).path("id").asLong();
    long outside = submit(login(user("无关", null)), menu, "不受影响", 201).path("id").asLong();

    send(
        put(ROLE, parent),
        adminToken,
        configuration(uniq("父"), List.of(menu, button), List.of()),
        200);

    assertThat(requestStatus(pending)).isEqualTo("SATISFIED");
    assertThat(requestStatus(withdrawn)).isEqualTo("WITHDRAWN");
    assertThat(requestStatus(unmatched)).isEqualTo("PENDING");
    assertThat(requestStatus(outside)).isEqualTo("PENDING");
    assertThat(
            jdbc.queryForObject(
                "select reviewer_user_id from sys_permission_request where id=?",
                Long.class,
                pending))
        .isNull();
  }

  @Test
  @DisplayName("验收10：审核时申请人已通过其他角色获得权限，申请直接转为已满足，不记录审核人")
  void 审核时已拥有权限转为已满足() throws Exception {
    long teacher = user("教师", null);
    long request = submit(login(teacher), menu, "需要菜单", 201).path("id").asLong();
    long granted = role(List.of(menu), List.of());
    jdbc.update("insert into sys_user_role(user_id, role_id) values (?, ?)", teacher, granted);

    JsonNode result = review(request, Map.of("decision", "REJECT"), 200);

    assertThat(result.path("status").asText()).isEqualTo("SATISFIED");
    assertThat(result.path("reviewerUserId").isNull()).isTrue();
  }

  @Test
  @DisplayName("RBAC0：只有申请人本人能撤回待审核申请，撤回后保留记录且不分配角色")
  void 撤回申请() throws Exception {
    String teacher = login(user("教师", null));
    long request = submit(teacher, menu, "需要课程", 201).path("id").asLong();

    assertProblem(
        send(post(WITHDRAWALS, request), login(user("他人", null)), null, 404),
        "NOT_FOUND",
        "申请不存在。");
    assertThat(send(post(WITHDRAWALS, request), teacher, null, 200).path("status").asText())
        .isEqualTo("WITHDRAWN");
    assertProblem(send(post(WITHDRAWALS, request), teacher, null, 409), "CONFLICT", "申请已处理。");
    JsonNode mine = send(get(PERMISSION_REQUESTS), teacher, null, 200);
    assertThat(mine.path("total").asLong()).isEqualTo(1);
    assertThat(mine.path("items").get(0).path("status").asText()).isEqualTo("WITHDRAWN");
    send(get(PERMISSION_REQUEST, 9_000_000L), teacher, null, 404);
  }

  @Test
  @DisplayName("RBAC0：部门管理员只能查看和审核管理范围内员工的申请，普通用户不能查看审核列表")
  void 部门管理员按管理范围审核() throws Exception {
    long college = department(uniq("学院"), null);
    long lab = department(uniq("实验室"), college);
    long elsewhere = department(uniq("他院"), null);
    String manager = login(user("部门管理员", college, departmentAdminRole));
    String inside = login(user("范围内", lab));
    String outside = login(user("范围外", elsewhere));
    long visible = submit(inside, menu, "范围内申请", 201).path("id").asLong();
    long hidden = submit(outside, menu, "范围外申请", 201).path("id").asLong();

    assertProblem(
        send(get(PERMISSION_REQUESTS).param("view", "REVIEW"), inside, null, 403),
        "FORBIDDEN",
        "当前角色无权管理用户和部门。");
    JsonNode reviewList =
        send(
            get(PERMISSION_REQUESTS).param("view", "REVIEW").param("status", "PENDING"),
            manager,
            null,
            200);
    assertThat(ids(reviewList.path("items"))).contains(visible).doesNotContain(hidden);
    assertThat(send(get(PERMISSION_REQUEST, visible), manager, null, 200).has("approvalRoles"))
        .isTrue();
    send(get(PERMISSION_REQUEST, hidden), manager, null, 404);
    send(post(REVIEWS, hidden), manager, Map.of("decision", "REJECT"), 404);
    JsonNode rejected = send(post(REVIEWS, visible), manager, Map.of("decision", "REJECT"), 200);
    assertThat(rejected.path("status").asText()).isEqualTo("REJECTED");
    send(get(PERMISSION_REQUESTS).param("view", "OTHER"), inside, null, 400);
  }

  @Test
  @DisplayName("RBAC0：通过须选择业务角色，拒绝不提交角色，否则请求不合法")
  void 审核请求格式校验() throws Exception {
    long request = submit(login(user("教师", null)), menu, "需要课程", 201).path("id").asLong();

    review(request, Map.of("decision", "APPROVE"), 400);
    review(request, Map.of("decision", "REJECT", "roleId", 1), 400);
    review(request, Map.of("decision", "MAYBE"), 400);
    assertThat(requestStatus(request)).isEqualTo("PENDING");
  }

  // ---------- 角色创建申请 ----------

  @Test
  @DisplayName("验收12：审批可调整父角色，申请保留原父角色快照，创建角色不自动分配给员工")
  void 审批可以更换父角色并保留申请快照() throws Exception {
    String originalName = uniq("原父角色");
    long parent = role(originalName, List.of(menu), List.of());
    long other = role(List.of(menu), List.of());
    String manager = login(manager());
    String name = uniq("审核助理");
    long request =
        send(post(ROLE_REQUESTS), manager, configuration(name, List.of(button), List.of(parent)), 201)
            .path("id")
            .asLong();

    JsonNode before = send(get(ROLE_REQUEST, request), adminToken, null, 200);
    assertThat(before.path("inheritedRoles").get(0).path("name").asText()).isEqualTo(originalName);
    assertThat(before.path("effectivePermissions")).hasSize(2);
    assertThat(before.path("effectivePermissions").get(1).path("sourceRoles")).isEmpty();
    send(put(ROLE, parent), adminToken, configuration(uniq("改名后"), List.of(menu), List.of()), 200);
    String finalName = uniq("调整后的审核助理");
    JsonNode approved =
        send(
            post(ROLE_REVIEWS, request),
            adminToken,
            Map.of("decision", "APPROVE", "role", configuration(finalName, List.of(button), List.of(other))),
            200);

    assertThat(approved.path("status").asText()).isEqualTo("APPROVED");
    long created = approved.path("createdRoleId").asLong();
    JsonNode role = send(get(ROLE, created), adminToken, null, 200);
    assertThat(role.path("name").asText()).isEqualTo(finalName);
    assertThat(ids(role.path("inheritedRoles"))).containsExactly(other);
    assertThat(count("select count(*) from sys_user_role where role_id=?", created)).isZero();
    JsonNode history = send(get(ROLE_REQUEST, request), manager, null, 200);
    assertThat(history.path("inheritedRoles").get(0).path("name").asText()).isEqualTo(originalName);
    assertThat(history.path("name").asText()).isEqualTo(name);
    assertProblem(
        send(post(ROLE_REVIEWS, request), adminToken, Map.of("decision", "REJECT"), 409),
        "CONFLICT",
        "申请已处理。");
    JsonNode logs =
        send(get(OPERATION_LOGS).param("keyword", name), adminToken, null, 200);
    assertThat(logs.path("items").findValuesAsText("action"))
        .contains("提交角色创建申请", "审核角色创建申请");
  }

  @Test
  @DisplayName("验收12：父角色后来获得的权限在预览中为 BOTH，审批时不能新增重复的直接配置，申请保持待审核")
  void 审批使用父角色最新配置校验() throws Exception {
    long parent = role(List.of(), List.of());
    String name = uniq("申请角色");
    long request =
        send(
                post(ROLE_REQUESTS),
                login(manager()),
                configuration(name, List.of(menu), List.of(parent)),
                201)
            .path("id")
            .asLong();
    send(put(ROLE, parent), adminToken, configuration(uniq("父"), List.of(menu), List.of()), 200);

    JsonNode detail = send(get(ROLE_REQUEST, request), adminToken, null, 200);
    assertThat(detail.path("effectivePermissions").get(0).path("acquisition").asText())
        .isEqualTo("BOTH");
    send(
        post(ROLE_REVIEWS, request),
        adminToken,
        Map.of("decision", "APPROVE", "role", configuration(name, List.of(menu), List.of(parent))),
        400);
    assertThat(
            jdbc.queryForObject(
                "select status from sys_role_create_request where id=?", String.class, request))
        .isEqualTo("PENDING");
    assertThat(count("select count(*) from sys_role where name=?", name)).isZero();
  }

  @Test
  @DisplayName("RBAC0：只有部门管理员能提交角色创建申请，部门管理员只看本人申请，审核仅限总管理员")
  void 角色创建申请的身份和可见范围() throws Exception {
    String manager = login(manager());
    String otherManager = login(manager());
    String name = uniq("新角色");
    long request =
        send(post(ROLE_REQUESTS), manager, configuration(name, List.of(menu), List.of()), 201)
            .path("id")
            .asLong();

    assertProblem(
        send(post(ROLE_REQUESTS), adminToken, configuration(uniq("总管"), List.of(), List.of()), 403),
        "FORBIDDEN",
        "仅部门管理员提交角色创建申请。");
    send(post(ROLE_REQUESTS), manager, configuration(uniq("缺菜单"), List.of(button), List.of()), 400);
    JsonNode mine = send(get(ROLE_REQUESTS), manager, null, 200);
    assertThat(mine.path("total").asLong()).isEqualTo(1);
    assertThat(mine.path("items").get(0).path("permissionIds").get(0).asLong()).isEqualTo(menu);
    assertThat(send(get(ROLE_REQUESTS), otherManager, null, 200).path("total").asLong()).isZero();
    assertThat(
            ids(send(get(ROLE_REQUESTS).param("status", "PENDING").param("pageSize", "100"),
                adminToken, null, 200).path("items")))
        .contains(request);
    send(get(ROLE_REQUESTS), login(user("普通用户", null)), null, 403);
    assertProblem(send(get(ROLE_REQUEST, request), otherManager, null, 404), "NOT_FOUND", "申请不存在。");
    assertProblem(
        send(post(ROLE_REVIEWS, request), manager, Map.of("decision", "REJECT"), 403),
        "FORBIDDEN",
        "没有该接口的调用权限。");
    send(
        post(ROLE_REVIEWS, request),
        adminToken,
        Map.of("decision", "REJECT", "role", configuration(name, List.of(), List.of())),
        400);
    JsonNode rejected =
        send(post(ROLE_REVIEWS, request), adminToken, Map.of("decision", "REJECT"), 200);
    assertThat(rejected.path("status").asText()).isEqualTo("REJECTED");
    assertThat(rejected.path("createdRoleId").isNull()).isTrue();
    send(post(ROLE_REVIEWS, 9_000_000L), adminToken, Map.of("decision", "REJECT"), 404);
  }

  // ---------- 辅助方法 ----------

  /** 新建属于一个新部门的部门管理员，返回用户 ID。 */
  private long manager() {
    return user("部门管理员", department(uniq("学院"), null), departmentAdminRole);
  }

  /** 提交权限申请并断言状态码。 */
  private JsonNode submit(String token, long permission, String reason, int expected)
      throws Exception {
    return send(
        post(PERMISSION_REQUESTS),
        token,
        Map.of("permissionId", permission, "reason", reason),
        expected);
  }

  /** 总管理员审核权限申请并断言状态码。 */
  private JsonNode review(long request, Map<String, ?> body, int expected) throws Exception {
    return send(post(REVIEWS, request), adminToken, body, expected);
  }

  /** 读取库中申请的当前状态。 */
  private String requestStatus(long request) {
    return jdbc.queryForObject(
        "select status from sys_permission_request where id=?", String.class, request);
  }

  /** 发送请求并断言状态码，返回解析后的响应体；body 为 null 时不带请求体。 */
  private JsonNode send(
      MockHttpServletRequestBuilder request, String token, Object body, int expected)
      throws Exception {
    if (body != null) {
      request.contentType("application/json").content(body(body));
    }
    String content =
        call(request, token)
            .andExpect(status().is(expected))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return content.isEmpty() ? json.nullNode() : json.readTree(content);
  }

  /** 断言 problem+json 的错误码和中文提示。 */
  private static void assertProblem(JsonNode problem, String code, String detail) {
    assertThat(problem.path("code").asText()).isEqualTo(code);
    assertThat(problem.path("detail").asText()).isEqualTo(detail);
  }

  /** 角色配置请求体：名称、直接权限和父角色。 */
  private static Map<String, Object> configuration(
      String name, List<Long> permissions, List<Long> parents) {
    return Map.of("name", name, "permissionIds", permissions, "inheritedRoleIds", parents);
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

  /** 在审批候选中找出指定角色。 */
  private static JsonNode findOption(JsonNode options, long roleId) {
    List<JsonNode> matches = new ArrayList<>();
    options.forEach(
        option -> {
          if (option.path("role").path("id").asLong() == roleId) {
            matches.add(option);
          }
        });
    assertThat(matches).hasSize(1);
    return matches.get(0);
  }

  /** 数组元素的 id 字段，保持原有顺序。 */
  private static List<Long> ids(JsonNode array) {
    List<Long> result = new ArrayList<>();
    array.forEach(node -> result.add(node.path("id").asLong()));
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
                uniq("p") + ":view",
                "type",
                parent == null ? "MENU" : "BUTTON"));
    if (parent != null) {
      values.put("parent_id", parent);
    }
    return insert("sys_permission", values);
  }

  /** 插入名称不重复的业务角色及其直接权限和父角色，返回角色 ID。 */
  private long role(List<Long> permissions, List<Long> parents) {
    return role(uniq("角色"), permissions, parents);
  }

  /** 插入指定名称的业务角色及其直接权限和父角色，返回角色 ID。 */
  private long role(String name, List<Long> permissions, List<Long> parents) {
    long id = insert("sys_role", Map.of("name", name, "description", "", "role_type", "BUSINESS"));
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
}
