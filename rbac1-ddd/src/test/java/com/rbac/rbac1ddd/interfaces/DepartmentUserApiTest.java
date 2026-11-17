package com.rbac.rbac1ddd.interfaces;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
 * 部门与用户接口集成测试：部门树、部门新增修改、用户查询新增修改，覆盖各业务错误码和操作记录。
 *
 * <p>期望值（错误码、中文提示、操作记录文字）与 rbac1-structured 的 DepartmentService、UserService 相同。
 */
class DepartmentUserApiTest extends ManagementApiTestSupport {

  /** 部门树与新增部门。 */
  private static final String DEPARTMENTS = "/api/v1/departments";

  /** 修改单个部门。 */
  private static final String DEPARTMENT = "/api/v1/departments/{departmentId}";

  /** 用户列表与新增用户。 */
  private static final String USERS = "/api/v1/users";

  /** 单个用户的查询和修改。 */
  private static final String USER = "/api/v1/users/{userId}";

  long totalAdminRole;
  long departmentAdminRole;
  long headquarters;
  long rnd;
  long frontend;
  long finance;
  long admin;
  String adminToken;

  /** 每个测试前准备独立的部门树（总部 → 研发部 → 前端组，总部 → 财务部）和已登录的总管理员。 */
  @BeforeEach
  void prepare() {
    totalAdminRole = id("select id from sys_role where role_type='TOTAL_ADMIN'");
    departmentAdminRole = id("select id from sys_role where role_type='DEPARTMENT_ADMIN'");
    headquarters = department(uniq("总部"), null);
    rnd = department(uniq("研发部"), headquarters);
    frontend = department(uniq("前端组"), rnd);
    finance = department(uniq("财务部"), headquarters);
    admin = user("管理员", null, totalAdminRole);
    adminToken = login(admin);
  }

  // ---------- 部门 ----------

  @Test
  @DisplayName("RBAC0：总管理员看到完整部门树，部门管理员只看到本部门及下级部门")
  void 部门树按管理范围返回() throws Exception {
    call(get(DEPARTMENTS), adminToken)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.departments[?(@.id==" + headquarters + ")].parentId").value(
            org.hamcrest.Matchers.contains((Object) null)))
        .andExpect(
            jsonPath("$.departments[?(@.id==" + headquarters + ")].children[*].id")
                .value(org.hamcrest.Matchers.contains((int) rnd, (int) finance)));

    String token = login(user("部门管理员", rnd, departmentAdminRole));
    call(get(DEPARTMENTS), token)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.departments.length()").value(1))
        .andExpect(jsonPath("$.departments[0].id").value(rnd))
        .andExpect(jsonPath("$.departments[0].parentId").value(headquarters))
        .andExpect(jsonPath("$.departments[0].children[0].id").value(frontend))
        .andExpect(jsonPath("$.departments[0].children[0].children.length()").value(0));
  }

  @Test
  @DisplayName("RBAC0：总管理员新增和修改部门并写入全局操作记录")
  void 总管理员新增和修改部门并写入操作记录() throws Exception {
    String name = uniq("测试学院");
    String childName = uniq("子部门");
    long top = createDepartment(name, null);
    long child =
        json.readTree(
                postDepartment(childName, top)
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.parentId").value(top))
                    .andReturn()
                    .getResponse()
                    .getContentAsString())
            .get("id")
            .asLong();
    assertThat(logDetail("新增部门", child)).isEqualTo("上级为「" + name + "」");
    assertThat(logDetail("新增部门", top)).isEqualTo("上级为顶层");

    String renamed = uniq("改名");
    call(
            patch(DEPARTMENT, child)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(nullable("name", renamed, "parentId", null))),
            adminToken)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(child))
        .andExpect(jsonPath("$.name").value(renamed))
        .andExpect(jsonPath("$.parentId").doesNotExist());
    assertThat(logDetail("修改部门", child))
        .isEqualTo("名称从「" + childName + "」改为「" + renamed + "」，上级从「" + name + "」改为顶层");
    assertThat(
            count(
                "select count(*) from sys_operation_log l join sys_operation_log_target t on t.operation_log_id=l.id"
                    + " where l.action='修改部门' and l.scope='GLOBAL' and t.target_type='DEPARTMENT'"
                    + " and t.target_id=? and t.target_name=? and t.department_id_at_event is null",
                child,
                renamed))
        .isEqualTo(1);

    call(
            patch(DEPARTMENT, child)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("name", renamed))),
            adminToken)
        .andExpect(status().isOk());
    assertThat(count("select count(*) from sys_operation_log_target t join sys_operation_log l"
                + " on l.id=t.operation_log_id where l.action='修改部门' and t.target_id=?", child))
        .isEqualTo(1);
  }

  @Test
  @DisplayName("RBAC0：部门不能移动到本部门或其下级部门下，引用不存在的部门时返回 404")
  void 部门移动防环和不存在的部门() throws Exception {
    patchDepartment(rnd, Map.of("parentId", frontend))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
        .andExpect(jsonPath("$.detail").value("上级部门不能是本部门或其下级部门。"));
    patchDepartment(rnd, Map.of("parentId", rnd))
        .andExpect(jsonPath("$.detail").value("上级部门不能是本部门或其下级部门。"));
    patchDepartment(rnd, Map.of("parentId", 999_999L))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.detail").value("上级部门不存在。"));
    patchDepartment(999_999L, Map.of("name", "x"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"))
        .andExpect(jsonPath("$.detail").value("部门不存在。"));
    patchDepartment(rnd, Map.of())
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value("请填写部门名称或上级部门。"));
    postDepartment("无上级", 999_999L)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.detail").value("部门不存在。"));
    call(
            post(DEPARTMENTS)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("name", "缺少上级"))),
            adminToken)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value("请检查请求参数及必填字段。"));
    patchDepartment(rnd, nullable("name", null))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value("请检查请求参数及必填字段。"));
    assertThat(jdbc.queryForObject("select parent_id from sys_department where id=?", Long.class, rnd))
        .isEqualTo(headquarters);
  }

  @Test
  @DisplayName("RBAC0：新增和修改部门仅限总管理员")
  void 新增和修改部门仅限总管理员() throws Exception {
    long role = insert("sys_role", Map.of("name", uniq("部门维护"), "description", "", "role_type", "BUSINESS"));
    for (String code : List.of("department:create", "department:update")) {
      jdbc.update(
          "insert into sys_role_permission(role_id, permission_id) select ?, id from sys_permission where code=?",
          role,
          code);
    }
    String token = login(user("部门管理员", rnd, departmentAdminRole, role));

    call(
            post(DEPARTMENTS)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(nullable("name", "越权", "parentId", null))),
            token)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"))
        .andExpect(jsonPath("$.detail").value("当前操作仅限总管理员。"));
    call(
            patch(DEPARTMENT, frontend)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("name", "越权"))),
            token)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.detail").value("当前操作仅限总管理员。"));
  }

  // ---------- 用户 ----------

  @Test
  @DisplayName("RBAC0：新增用户不返回初始密码，只保存摘要，并可同时任命部门管理员")
  void 新增用户不返回初始密码并可任命部门管理员() throws Exception {
    String username = uniq("teacher");
    MvcResult result =
        createUser(username, "教师", frontend, Map.of("departmentAdministrator", true))
            .andExpect(status().isCreated())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.user.username").value(username))
            .andExpect(jsonPath("$.user.name").value("教师"))
            .andExpect(jsonPath("$.user.department.id").value(frontend))
            .andExpect(jsonPath("$.user.status").value("ACTIVE"))
            .andExpect(jsonPath("$.user.departmentAdministrator").value(true))
            .andExpect(jsonPath("$.user.businessRoles.length()").value(0))
            .andReturn();
    String content = result.getResponse().getContentAsString();
    assertThat(content).doesNotContain("initialPassword", "demo1234");
    long id = json.readTree(content).path("user").path("id").asLong();

    assertThat(jdbc.queryForObject("select password_hash from sys_user where id=?", String.class, id))
        .startsWith("$argon2");
    assertThat(
            count(
                "select count(*) from sys_operation_log l join sys_operation_log_target t on t.operation_log_id=l.id"
                    + " where l.action='新增用户' and l.scope='USER_SCOPE' and l.detail='初始状态为ACTIVE'"
                    + " and t.target_type='USER' and t.target_id=? and t.department_id_at_event=?",
                id,
                frontend))
        .isEqualTo(1);
    assertThat(
            count(
                "select count(*) from sys_operation_log l join sys_operation_log_target t on t.operation_log_id=l.id"
                    + " where l.action='任命部门管理员' and l.scope='GLOBAL' and l.detail='任命部门管理员'"
                    + " and t.target_id=? and t.department_id_at_event is null",
                id))
        .isEqualTo(1);
  }

  @Test
  @DisplayName("RBAC0：用户名重复时新增和修改都返回 409，并记录失败操作")
  void 用户名重复时返回409() throws Exception {
    String username = uniq("dup");
    createUser(username, "甲", frontend, Map.of()).andExpect(status().isCreated());
    long other = user("乙", frontend);

    createUser(username, "丙", frontend, Map.of())
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CONFLICT"))
        .andExpect(jsonPath("$.title").value("数据已存在"))
        .andExpect(jsonPath("$.detail").value("该用户名已存在。"));
    patchUser(other, Map.of("username", username), adminToken)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail").value("该用户名已存在。"));
    assertThat(count("select count(*) from sys_user where username=?", username)).isEqualTo(1);
    assertThat(
            count(
                "select count(*) from sys_operation_log where result='FAILURE' and detail=?",
                "POST " + USERS + "，CONFLICT"))
        .isGreaterThanOrEqualTo(1);
  }

  @Test
  @DisplayName("RBAC0：用户列表按部门（含下级部门）、关键字和部门名称筛选并分页")
  void 用户列表按部门关键字筛选并分页() throws Exception {
    String keyword = uniq("检索");
    long first = user(keyword + "一", rnd);
    long second = user(keyword + "二", frontend);
    user(keyword + "三", finance);
    long role = insert("sys_role", Map.of("name", uniq("业务"), "description", "", "role_type", "BUSINESS"));
    jdbc.update("insert into sys_user_role(user_id, role_id) values (?, ?)", second, role);
    jdbc.update("insert into sys_user_role(user_id, role_id) values (?, ?)", second, departmentAdminRole);

    call(get(USERS).param("departmentId", Long.toString(rnd)).param("keyword", keyword), adminToken)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(2))
        .andExpect(jsonPath("$.page").value(1))
        .andExpect(jsonPath("$.pageSize").value(20))
        .andExpect(jsonPath("$.items[0].id").value(first))
        .andExpect(jsonPath("$.items[1].id").value(second))
        .andExpect(jsonPath("$.items[1].department.id").value(frontend))
        .andExpect(jsonPath("$.items[1].departmentAdministrator").value(true))
        .andExpect(jsonPath("$.items[1].businessRoles[0].id").value(role))
        .andExpect(jsonPath("$.items[1].businessRoles.length()").value(1));
    call(
            get(USERS)
                .param("departmentId", Long.toString(rnd))
                .param("keyword", keyword)
                .param("page", "2")
                .param("pageSize", "1"),
            adminToken)
        .andExpect(jsonPath("$.total").value(2))
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].id").value(second));

    String departmentName = jdbc.queryForObject("select name from sys_department where id=?", String.class, frontend);
    call(get(USERS).param("departmentId", Long.toString(rnd)).param("keyword", departmentName), adminToken)
        .andExpect(jsonPath("$.total").value(0));
    call(
            get(USERS)
                .param("departmentId", Long.toString(rnd))
                .param("keyword", departmentName)
                .param("includeDepartmentName", "true"),
            adminToken)
        .andExpect(jsonPath("$.total").value(1))
        .andExpect(jsonPath("$.items[0].id").value(second));
    call(get(USERS).param("pageSize", "101"), adminToken).andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("RBAC0：部门管理员只能查询本部门及下级部门的用户")
  void 部门管理员只能查询管理范围内的用户() throws Exception {
    String token = login(user("部门管理员", rnd, departmentAdminRole));
    long inside = user("组员", frontend);
    long outside = user("财务", finance);

    call(get(USERS), token)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(2));
    call(get(USERS).param("departmentId", Long.toString(finance)), token)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("OUTSIDE_MANAGEMENT_SCOPE"))
        .andExpect(jsonPath("$.detail").value("请选择本部门或下级部门内的对象。"));
    call(get(USERS).param("departmentId", "999999"), token)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.detail").value("部门不存在。"));
    call(get(USER, inside), token)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.department.id").value(frontend));
    call(get(USER, outside), token)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("OUTSIDE_MANAGEMENT_SCOPE"));
    patchUser(outside, Map.of("name", "改名"), token)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("OUTSIDE_MANAGEMENT_SCOPE"));
    createUser(uniq("u"), "越界", finance, Map.of(), token)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("OUTSIDE_MANAGEMENT_SCOPE"));
  }

  @Test
  @DisplayName("RBAC0：查询或修改不存在的用户、没有部门的账号时返回 404")
  void 不存在的用户返回404() throws Exception {
    call(get(USER, 999_999L), adminToken)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"))
        .andExpect(jsonPath("$.detail").value("用户不存在。"));
    call(get(USER, admin), adminToken).andExpect(status().isNotFound());
    patchUser(999_999L, Map.of("name", "x"), adminToken)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.detail").value("用户不存在。"));
    createUser(uniq("u"), "无部门", 999_999L, Map.of())
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.detail").value("部门不存在。"));
    patchUser(user("员工", frontend), Map.of("departmentId", 999_999L), adminToken)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.detail").value("部门不存在。"));
  }

  @Test
  @DisplayName("RBAC0：修改用户资料写入操作记录，空请求返回 400")
  void 修改用户资料写入操作记录() throws Exception {
    long member = user("员工", frontend);
    String oldUsername = jdbc.queryForObject("select username from sys_user where id=?", String.class, member);
    String newUsername = uniq("renamed");

    patchUser(member, Map.of("username", newUsername, "name", "新名"), adminToken)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.username").value(newUsername))
        .andExpect(jsonPath("$.name").value("新名"));
    assertThat(logDetail("修改用户", member))
        .isEqualTo("用户名从「" + oldUsername + "」改为「" + newUsername + "」，姓名从「员工」改为「新名」");
    patchUser(member, Map.of(), adminToken)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
        .andExpect(jsonPath("$.detail").value("请填写要修改的用户信息。"));
    patchUser(member, Map.of("status", "LOCKED"), adminToken).andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("RBAC0：跨部门调动和任免部门管理员仅限总管理员，调动保留业务角色")
  void 跨部门调动和任免部门管理员仅限总管理员() throws Exception {
    long role = insert("sys_role", Map.of("name", uniq("业务"), "description", "", "role_type", "BUSINESS"));
    long member = user("员工", frontend, role);
    String token = login(user("部门管理员", rnd, departmentAdminRole));

    patchUser(member, Map.of("departmentId", rnd), token)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"))
        .andExpect(jsonPath("$.detail").value("只有总管理员可以调动用户或任免部门管理员。"));
    patchUser(member, Map.of("departmentAdministrator", false), token)
        .andExpect(jsonPath("$.detail").value("只有总管理员可以调动用户或任免部门管理员。"));
    createUser(uniq("u"), "新人", frontend, Map.of("departmentAdministrator", false), token)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.detail").value("只有总管理员可以调动用户或任免部门管理员。"));

    patchUser(member, Map.of("departmentId", finance, "departmentAdministrator", true), adminToken)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.department.id").value(finance))
        .andExpect(jsonPath("$.departmentAdministrator").value(true))
        .andExpect(jsonPath("$.businessRoles[0].id").value(role));
    String from = jdbc.queryForObject("select name from sys_department where id=?", String.class, frontend);
    String to = jdbc.queryForObject("select name from sys_department where id=?", String.class, finance);
    assertThat(logDetail("跨部门调动", member)).isEqualTo("从「" + from + "」调至「" + to + "」");
    assertThat(logDetail("任命部门管理员", member)).isEqualTo("任命部门管理员");

    patchUser(member, Map.of("departmentAdministrator", false), adminToken)
        .andExpect(jsonPath("$.departmentAdministrator").value(false));
    assertThat(logDetail("撤销部门管理员", member)).isEqualTo("撤销部门管理员");
    assertThat(jdbc.queryForList("select role_id from sys_user_role where user_id=?", Long.class, member))
        .containsExactly(role);
  }

  @Test
  @DisplayName("RBAC0：停用账号立即撤销其全部会话，启用和停用写入操作记录")
  void 停用账号撤销全部会话() throws Exception {
    long member = user("员工", frontend);
    String first = login(member);
    login(member);

    patchUser(member, Map.of("status", "DISABLED"), adminToken)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("DISABLED"));
    assertThat(count("select count(*) from sys_session where user_id=?", member)).isZero();
    call(get("/auth/me"), first).andExpect(status().isUnauthorized());
    assertThat(logDetail("停用账号", member)).isEqualTo("状态从 ACTIVE 改为 DISABLED");

    patchUser(member, Map.of("status", "ACTIVE"), adminToken).andExpect(status().isOk());
    assertThat(logDetail("启用账号", member)).isEqualTo("状态从 DISABLED 改为 ACTIVE");
  }

  // ---------- 辅助方法 ----------

  /** 以总管理员身份提交新增部门请求，parentId 为 null 时显式传 null 表示顶级部门。 */
  private ResultActions postDepartment(String name, Long parentId) throws Exception {
    return call(
        post(DEPARTMENTS)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body(nullable("name", name, "parentId", parentId))),
        adminToken);
  }

  /** 新增部门并断言返回 201，返回新部门 ID。 */
  private long createDepartment(String name, Long parentId) throws Exception {
    MvcResult response =
        postDepartment(name, parentId).andExpect(status().isCreated()).andReturn();
    return json.readTree(response.getResponse().getContentAsString()).get("id").asLong();
  }

  /** 以总管理员身份修改部门。 */
  private ResultActions patchDepartment(long id, Map<String, ?> changes) throws Exception {
    return call(
        patch(DEPARTMENT, id).contentType(MediaType.APPLICATION_JSON).content(body(changes)),
        adminToken);
  }

  /** 以总管理员身份新增用户，初始密码固定为 demo1234，extra 中是可选字段。 */
  private ResultActions createUser(String username, String name, long departmentId, Map<String, ?> extra)
      throws Exception {
    return createUser(username, name, departmentId, extra, adminToken);
  }

  /** 以指定操作者身份新增用户，初始密码固定为 demo1234，extra 中是可选字段。 */
  private ResultActions createUser(
      String username, String name, long departmentId, Map<String, ?> extra, String token)
      throws Exception {
    Map<String, Object> request =
        new HashMap<>(
            Map.of(
                "username",
                username,
                "name",
                name,
                "departmentId",
                departmentId,
                "initialPassword",
                "demo1234"));
    request.putAll(extra);
    return call(
        post(USERS).contentType(MediaType.APPLICATION_JSON).content(body(request)), token);
  }

  /** 以指定操作者身份修改用户。 */
  private ResultActions patchUser(long id, Map<String, ?> changes, String token) throws Exception {
    return call(patch(USER, id).contentType(MediaType.APPLICATION_JSON).content(body(changes)), token);
  }

  /** 构造允许 null 值的请求体，参数依次为键、值。 */
  private static Map<String, Object> nullable(Object... keyValues) {
    Map<String, Object> values = new HashMap<>();
    for (int i = 0; i < keyValues.length; i += 2) {
      values.put((String) keyValues[i], keyValues[i + 1]);
    }
    return values;
  }

  /** 读取某个对象最近一条指定动作的成功操作记录明细。 */
  private String logDetail(String action, long targetId) {
    return jdbc.queryForObject(
        "select l.detail from sys_operation_log l join sys_operation_log_target t on t.operation_log_id=l.id"
            + " where l.action=? and l.result='SUCCESS' and t.target_id=? order by l.id desc limit 1",
        String.class,
        action,
        targetId);
  }
}
