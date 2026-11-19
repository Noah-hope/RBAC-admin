package com.rbac.rbac1ddd.interfaces;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rbac.rbac1ddd.application.auth.PasswordHasher;
import com.rbac.rbac1ddd.infrastructure.security.TokenHash;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 统一身份接口测试：登录、退出、当前用户、权限检查和签名公钥，行为与 rbac1-structured 相同。
 *
 * <p>令牌由本模块自己签发；账号密码使用与结构化版本相同参数的 Argon2 摘要。
 */
@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:session;MODE=MySQL;DB_CLOSE_DELAY=-1",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.jpa.hibernate.ddl-auto=create-drop",
      "spring.flyway.enabled=false",
      "spring.jpa.defer-datasource-initialization=true",
      "spring.sql.init.mode=always",
      "spring.sql.init.data-locations=classpath:test-bootstrap.sql",
      "rbac.bootstrap.username=admin",
      "rbac.bootstrap.password=demo1234"
    })
@AutoConfigureMockMvc
class SessionApiTest {

  /** 统一登录。 */
  private static final String LOGIN = "/auth/login";

  /** 退出当前登录。 */
  private static final String LOGOUT = "/auth/logout";

  /** 当前用户信息。 */
  private static final String ME = "/auth/me";

  /** 前端菜单、按钮权限检查。 */
  private static final String PERMISSION_CHECK = "/auth/permissions/{permission}";

  /** 后端接口权限检查。 */
  private static final String API_CHECK = "/auth/api-permissions/{operationId}";

  /** 签名公钥集合。 */
  private static final String JWKS = "/.well-known/jwks.json";

  /** 权限中心的管理接口，用于验证令牌受众。 */
  private static final String ROLES = "/api/v1/roles";

  private static final String PASSWORD = "secret123";
  private static final AtomicInteger SEQUENCE = new AtomicInteger();

  @Autowired
  MockMvc mvc;

  @Autowired
  ObjectMapper json;

  @Autowired
  JdbcTemplate jdbc;

  @Autowired
  PasswordHasher passwords;

  /** 生成测试用 RSA 密钥对，私钥和公钥分别写入临时 PEM 文件。 */
  @DynamicPropertySource
  static void signingKeys(DynamicPropertyRegistry registry) throws Exception {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    KeyPair keys = generator.generateKeyPair();
    Path directory = Files.createTempDirectory("rbac-ddd-session-keys");
    Path privateFile = directory.resolve("private.pem");
    Path publicFile = directory.resolve("public.pem");
    Files.writeString(
        privateFile,
        "-----BEGIN PRIVATE KEY-----\n"
            + Base64.getMimeEncoder(64, new byte[] {10})
                .encodeToString(keys.getPrivate().getEncoded())
            + "\n-----END PRIVATE KEY-----");
    Files.writeString(
        publicFile,
        "-----BEGIN PUBLIC KEY-----\n"
            + Base64.getMimeEncoder(64, new byte[] {10})
                .encodeToString(keys.getPublic().getEncoded())
            + "\n-----END PUBLIC KEY-----");
    registry.add("rbac.jwt.private-key", () -> privateFile.toUri().toString());
    registry.add("rbac.jwt.public-key", () -> publicFile.toUri().toString());
  }

  @Test
  @DisplayName("RBAC0：首次启动创建总管理员，密码保存为与结构化版本参数相同的 Argon2 摘要")
  void 首次启动创建总管理员() {
    String hash =
        jdbc.queryForObject(
            "select password_hash from sys_user where username='admin'", String.class);
    assertThat(hash).startsWith("$argon2id$v=19$m=19456,t=2,p=1$");
    assertThat(
            jdbc.queryForObject(
                "select count(*) from sys_user_role ur join sys_role r on r.id=ur.role_id"
                    + " join sys_user u on u.id=ur.user_id"
                    + " where u.username='admin' and r.role_type='TOTAL_ADMIN'",
                Long.class))
        .isEqualTo(1);
  }

  @Test
  @DisplayName("RBAC0：登录成功签发 Bearer 令牌，禁止缓存，会话表只保存令牌摘要")
  void 登录成功签发令牌并只保存摘要() throws Exception {
    String username = user("员工", "ACTIVE");
    String body =
        login(username, PASSWORD, List.of("permission-center"), "10.0.1.1")
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.tokenType").value("Bearer"))
            .andExpect(jsonPath("$.expiresIn").value(7200))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String token = json.readTree(body).get("accessToken").asText();
    assertThat(
            jdbc.queryForObject(
                "select count(*) from sys_session where token_hash=?",
                Long.class,
                (Object) TokenHash.sha256(token)))
        .isEqualTo(1);
    call(get(ME), token).andExpect(status().isOk()).andExpect(jsonPath("$.no").value(username));
  }

  @Test
  @DisplayName("RBAC0：账号或密码错误返回 401，停用账号返回 ACCOUNT_DISABLED")
  void 账号密码错误和停用账号不能登录() throws Exception {
    String active = user("员工", "ACTIVE");
    String disabled = user("离职员工", "DISABLED");
    login(active, "wrong-password", List.of("permission-center"), "10.0.2.1")
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    login("nobody", PASSWORD, List.of("permission-center"), "10.0.2.1")
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    login(disabled, PASSWORD, List.of("permission-center"), "10.0.2.1")
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"))
        .andExpect(jsonPath("$.detail").value("请联系管理员确认账号状态。"));
  }

  @Test
  @DisplayName("RBAC0：登录选择未登记或重复的应用时返回 400")
  void 登录选择未登记或重复的应用时返回400() throws Exception {
    String username = user("员工", "ACTIVE");
    login(username, PASSWORD, List.of("unknown-app"), "10.0.3.1")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
        .andExpect(jsonPath("$.detail").value("请选择已登记的应用。"));
    login(username, PASSWORD, List.of("permission-center", "permission-center"), "10.0.3.1")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value("请选择已登记的应用。"));
    login(username, "wrong-password", List.of("unknown-app"), "10.0.3.1")
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("RBAC0：同一地址一分钟内失败 5 次后返回 429 并提示重试时间")
  void 同一地址连续失败后限制登录() throws Exception {
    String username = user("员工", "ACTIVE");
    for (int attempt = 0; attempt < 5; attempt++) {
      login(username, "wrong-password", List.of("permission-center"), "10.0.4.1")
          .andExpect(status().isUnauthorized());
    }
    login(username, PASSWORD, List.of("permission-center"), "10.0.4.1")
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"))
        .andExpect(header().exists("Retry-After"));
    login(username, PASSWORD, List.of("permission-center"), "10.0.4.2")
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("验收1：当前用户信息按目标应用返回角色和含继承来源的权限")
  void 当前用户信息按目标应用返回角色和继承权限() throws Exception {
    long course = insert("sys_application", Map.of("code", uniq("course"), "name", uniq("课程")));
    long other = insert("sys_application", Map.of("code", uniq("other"), "name", uniq("其他")));
    String courseCode = code(course);
    long menu = permission(course, "课程管理", uniq("course") + ":view", null);
    long button = permission(course, "新增课程", uniq("course") + ":create", menu);
    long otherMenu = permission(other, "其他菜单", uniq("other") + ":view", null);
    long parent = role("父", menu, button);
    long child = role("子");
    jdbc.update(
        "insert into sys_role_inheritance(role_id, inherited_role_id) values (?, ?)", child, parent);
    long unrelated = role("无关", otherMenu);
    String username = user("员工", "ACTIVE", child, unrelated);
    String token = token(username, List.of(courseCode, code(other)));

    call(get(ME), token)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value("请指定目标应用。"));
    call(get(ME).header("X-Application-Code", "permission-center"), token)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("APPLICATION_MISMATCH"));
    String body =
        call(get(ME).header("X-Application-Code", courseCode), token)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.subject").isString())
            .andExpect(jsonPath("$.roles[0]").value(Long.toString(child)))
            .andExpect(jsonPath("$.roles.length()").value(1))
            .andExpect(jsonPath("$.permissions.length()").value(2))
            .andExpect(jsonPath("$.name").value("员工"))
            .andExpect(jsonPath("$.no").value(username))
            .andExpect(jsonPath("$.departmentId").isEmpty())
            .andExpect(jsonPath("$.managementRole").value("NONE"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(json.readTree(body).fieldNames())
        .toIterable()
        .containsExactly(
            "subject",
            "roles",
            "permissions",
            "name",
            "no",
            "id",
            "departmentId",
            "managementRole");
    call(get(ROLES), token)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("APPLICATION_MISMATCH"));
  }

  @Test
  @DisplayName("验收3：前端权限检查包含继承得到的权限")
  void 前端权限检查包含继承权限() throws Exception {
    long course = insert("sys_application", Map.of("code", uniq("course"), "name", uniq("课程")));
    String menuCode = uniq("course") + ":view";
    long menu = permission(course, "课程管理", menuCode, null);
    long parent = role("父", menu);
    long child = role("子");
    jdbc.update(
        "insert into sys_role_inheritance(role_id, inherited_role_id) values (?, ?)", child, parent);
    String username = user("员工", "ACTIVE", child);
    String token = token(username, List.of(code(course)));

    call(get(PERMISSION_CHECK, menuCode), token)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.allowed").value(true));
    call(get(PERMISSION_CHECK, "course:missing"), token)
        .andExpect(jsonPath("$.allowed").value(false));
    // 编码格式由接入系统决定：不含冒号的编码同样按是否拥有判断，只限制长度。
    call(get(PERMISSION_CHECK, "no-colon"), token)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.allowed").value(false));
    call(get(PERMISSION_CHECK, "x".repeat(65)), token).andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("RBAC0：接口权限检查按关联权限判断，自助接口对已登录用户开放，未登记接口返回 404")
  void 接口权限检查() throws Exception {
    long course = insert("sys_application", Map.of("code", uniq("course"), "name", uniq("课程")));
    long menu = permission(course, "课程管理", uniq("course") + ":view", null);
    long api =
        insert(
            "sys_api",
            Map.of(
                "application_id",
                course,
                "operation_id",
                "listCourses",
                "http_method",
                "GET",
                "path",
                "/courses"));
    jdbc.update("insert into sys_api_permission(permission_id, api_id) values (?, ?)", menu, api);
    long parent = role("父", menu);
    long child = role("子");
    jdbc.update(
        "insert into sys_role_inheritance(role_id, inherited_role_id) values (?, ?)", child, parent);
    String holder = token(user("员工", "ACTIVE", child), List.of(code(course)));
    String stranger =
        token(user("访客", "ACTIVE"), List.of(code(course), "permission-center"));

    call(get(API_CHECK, "listCourses"), holder)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.operationId").value("listCourses"))
        .andExpect(jsonPath("$.allowed").value(true));
    call(get(API_CHECK, "listCourses").header("X-Application-Code", code(course)), stranger)
        .andExpect(jsonPath("$.allowed").value(false));
    call(get(API_CHECK, "getMyPermissions").header("X-Application-Code", "permission-center"), stranger)
        .andExpect(jsonPath("$.allowed").value(true));
    call(get(API_CHECK, "listRoles").header("X-Application-Code", "permission-center"), stranger)
        .andExpect(jsonPath("$.allowed").value(false));
    call(get(API_CHECK, "missing"), holder)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.detail").value("API未登记。"));
  }

  @Test
  @DisplayName("RBAC0：退出需要有效令牌，签名公钥无需登录且不含私钥")
  void 退出需要有效令牌且公钥公开() throws Exception {
    mvc.perform(post(LOGOUT)).andExpect(status().isUnauthorized());
    mvc.perform(post(LOGOUT).header("Authorization", "Bearer not-a-jwt"))
        .andExpect(status().isUnauthorized());
    mvc.perform(get(ME)).andExpect(status().isUnauthorized());
    mvc.perform(get(JWKS))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.keys.length()").value(1))
        .andExpect(jsonPath("$.keys[0].kty").value("RSA"))
        .andExpect(jsonPath("$.keys[0].use").value("sig"))
        .andExpect(jsonPath("$.keys[0].kid").value("rbac-signing-key"))
        .andExpect(jsonPath("$.keys[0].p").doesNotExist());
  }

  @Test
  @DisplayName("RBAC0：已登录用户没有接口权限的写操作被拒绝时记录失败日志")
  void 没有接口权限的写操作记录失败日志() throws Exception {
    String username = user("越权员工", "ACTIVE");
    String token = token(username, List.of("permission-center"));
    call(
            post(ROLES)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json.writeValueAsString(
                        Map.of(
                            "name", "x", "permissionIds", List.of(), "inheritedRoleIds", List.of()))),
            token)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.detail").value("没有该接口的调用权限。"));
    assertThat(
            jdbc.queryForObject(
                "select count(*) from sys_operation_log where result='FAILURE' and actor_name=?",
                Long.class,
                "越权员工"))
        .isEqualTo(1);
  }

  // ---------- 辅助方法 ----------

  /** 从指定远程地址提交登录请求。 */
  private ResultActions login(
      String username, String password, List<String> systems, String address) throws Exception {
    return mvc.perform(
        post(LOGIN)
            .with(
                request -> {
                  request.setRemoteAddr(address);
                  return request;
                })
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                json.writeValueAsString(
                    Map.of("username", username, "password", password, "systems", systems))));
  }

  /** 登录指定应用并返回访问令牌，每个账号使用独立的远程地址，避免影响登录限流。 */
  private String token(String username, List<String> systems) throws Exception {
    String body =
        login(username, PASSWORD, systems, "10.1.0." + SEQUENCE.incrementAndGet())
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(body).get("accessToken").asText();
  }

  /** 带上 Bearer 令牌执行请求。 */
  private ResultActions call(MockHttpServletRequestBuilder request, String token)
      throws Exception {
    return mvc.perform(request.header("Authorization", "Bearer " + token));
  }

  /** 插入没有部门的用户，密码为 {@link #PASSWORD}，并直接关联给定角色；返回登录名。 */
  private String user(String name, String status, long... roleIds) {
    String username = uniq("u");
    long id =
        insert(
            "sys_user",
            Map.of(
                "username",
                username,
                "name",
                name,
                "password_hash",
                passwords.hash(PASSWORD),
                "status",
                status));
    for (long roleId : roleIds) {
      jdbc.update("insert into sys_user_role(user_id, role_id) values (?, ?)", id, roleId);
    }
    return username;
  }

  /** 插入业务角色并直接配置给定权限。 */
  private long role(String name, long... permissionIds) {
    long id =
        insert("sys_role", Map.of("name", uniq(name), "description", "", "role_type", "BUSINESS"));
    for (long permissionId : permissionIds) {
      jdbc.update(
          "insert into sys_role_permission(role_id, permission_id) values (?, ?)",
          id,
          permissionId);
    }
    return id;
  }

  /** 在指定应用下插入权限：没有父级时是菜单，有父级时是该菜单下的按钮。 */
  private long permission(long applicationId, String name, String code, Long parent) {
    Map<String, Object> values =
        new HashMap<>(
            Map.of(
                "application_id",
                applicationId,
                "name",
                name,
                "code",
                code,
                "type",
                parent == null ? "MENU" : "BUTTON"));
    if (parent != null) {
      values.put("parent_id", parent);
    }
    return insert("sys_permission", values);
  }

  /** 查询应用编码。 */
  private String code(long applicationId) {
    return jdbc.queryForObject(
        "select code from sys_application where id=?", String.class, applicationId);
  }

  /** 向指定表插入一行并返回自增主键。 */
  private long insert(String table, Map<String, ?> values) {
    return new SimpleJdbcInsert(jdbc)
        .withTableName(table)
        .usingGeneratedKeyColumns("id")
        .executeAndReturnKey(new HashMap<>(values))
        .longValue();
  }

  /** 在前缀后追加全局递增序号，避免测试之间名称或编码冲突。 */
  private static String uniq(String prefix) {
    return prefix + SEQUENCE.incrementAndGet();
  }
}
