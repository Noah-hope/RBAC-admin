package com.rbac.rbac1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.springframework.http.HttpHeaders;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.yaml.snakeyaml.Yaml;

/**
 * 两种实现共用的验收测试：rbac1-ddd 的 {@code SharedAcceptanceTest} 逐条移植自本类。
 *
 * <p>启动完整的 Spring 应用，用 MockMvc 经过真实的过滤器、拦截器和异常处理发送请求。测试数据来自
 * test-bootstrap.sql，启动时创建总管理员 admin/demo1234。修改这里的请求、状态码或断言时，
 * 必须同步修改 rbac1-ddd 中对应的用例，保证两种实现通过相同的验收条件。
 */
@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:http;MODE=MySQL;DB_CLOSE_DELAY=-1",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.jpa.hibernate.ddl-auto=create-drop",
      "spring.flyway.enabled=false",
      "spring.jpa.defer-datasource-initialization=true",
      "spring.sql.init.mode=always",
      "spring.sql.init.data-locations=classpath:test-bootstrap.sql",
      "rbac.bootstrap.username=admin",
      "rbac.bootstrap.password=demo1234",
      "rbac.cors.allowed-origins=https://course.test.example"
    })
@AutoConfigureMockMvc
class RbacApiTest {

  /** 课程系统使用的统一身份目录接口。 */
  private static final String DIRECTORY_DEPARTMENTS = "/auth/departments";
  private static final String DIRECTORY_DEPARTMENT_USERS = "/auth/departments/{departmentId}/users";
  private static final String DIRECTORY_USER = "/auth/users/{userId}";
  private static final String COURSE_APPLICATION = "course-project-management";
  private static final String COURSE_ORIGIN = "https://course.test.example";

  /** 统一登录。 */
  private static final String LOGIN = "/auth/login";

  /** 退出当前登录。 */
  private static final String LOGOUT = "/auth/logout";

  /** 当前用户信息。 */
  private static final String ME = "/auth/me";

  /** 签名公钥集合。 */
  private static final String JWKS = "/.well-known/jwks.json";

  /** 接口权限检查。 */
  private static final String API_PERMISSION = "/auth/api-permissions/{operationId}";

  /** 菜单或按钮权限检查。 */
  private static final String PERMISSION = "/auth/permissions/{permission}";

  /** 部门树与新增部门。 */
  private static final String DEPARTMENTS = "/api/v1/departments";

  /** 用户列表与新增用户。 */
  private static final String USERS = "/api/v1/users";

  /** 单个用户的查询和修改。 */
  private static final String USER = "/api/v1/users/{userId}";

  /** 应用列表与新增应用。 */
  private static final String APPLICATIONS = "/api/v1/applications";

  /** 应用权限树与登记权限。 */
  private static final String PERMISSIONS = "/api/v1/applications/{applicationId}/permissions";

  /** 单个权限的查看和修改。 */
  private static final String PERMISSION_ITEM =
      "/api/v1/applications/{applicationId}/permissions/{permissionId}";

  /** 应用 API 清单与登记 API。 */
  private static final String APIS = "/api/v1/applications/{applicationId}/apis";

  /** 操作记录查询。 */
  private static final String OPERATION_LOGS = "/api/v1/operation-logs";

  /** 角色列表与创建。 */
  private static final String ROLES = "/api/v1/roles";

  /** 单个角色的查询、修改和删除。 */
  private static final String ROLE = "/api/v1/roles/{roleId}";

  /** 整体替换单个用户的角色。 */
  private static final String USER_ROLES = "/api/v1/users/{userId}/roles";

  /** 当前用户的权限。 */
  private static final String MY_PERMISSIONS = "/api/v1/me/permissions";

  /** 角色创建申请列表与提交。 */
  private static final String ROLE_CREATION_REQUESTS = "/api/v1/role-creation-requests";

  /** 单个角色创建申请的详情。 */
  private static final String ROLE_CREATION_REQUEST = "/api/v1/role-creation-requests/{requestId}";

  /** 审核角色创建申请。 */
  private static final String ROLE_CREATION_REVIEWS =
      "/api/v1/role-creation-requests/{requestId}/reviews";

  /** 接口契约文件，相对于 rbac1-structured 模块目录。 */
  private static final String API_DOCUMENT = "../docs/03-api/RBAC1-api.yaml";

  /** 从仓库根目录运行测试时的接口契约文件位置。 */
  private static final String API_DOCUMENT_FROM_ROOT = "docs/03-api/RBAC1-api.yaml";

  @Autowired
  MockMvc mvc;

  @Autowired
  ObjectMapper json;

  @Autowired
  JdbcTemplate jdbc;

  @Autowired
  RequestMappingHandlerMapping mappings;

  /** 生成测试用 RSA 密钥对，私钥和公钥分别写入临时 PEM 文件，作为签发和校验 JWT 的密钥。 */
  @DynamicPropertySource
  static void signingKeys(DynamicPropertyRegistry registry) throws Exception {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    KeyPair keys = generator.generateKeyPair();
    Path directory = Files.createTempDirectory("rbac-test-keys");
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
  @DisplayName("RBAC0：统一登录后查询当前用户，退出只使当前令牌失效")
  void unifiedLoginMeAndLogoutInvalidateOnlyTheCurrentToken() throws Exception {
    String first = login("admin");
    String second = login("admin");
    mvc.perform(get(ME).header("Authorization", "Bearer " + first))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.no").value("admin"))
        .andExpect(jsonPath("$.managementRole").value("TOTAL_ADMIN"));
    mvc.perform(get(JWKS))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.keys[0].alg").value("RS256"))
        .andExpect(jsonPath("$.keys[0].d").doesNotExist());
    mvc.perform(post(LOGOUT).header("Authorization", "Bearer " + first))
        .andExpect(status().isNoContent());
    mvc.perform(get(ME).header("Authorization", "Bearer " + first))
        .andExpect(status().isUnauthorized());
    mvc.perform(get(ME).header("Authorization", "Bearer " + second))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("RBAC0：一个 API 可被多项权限共享，解除其中一项关联仍保留 API 登记")
  void apiRegistrySupportsSharedAssociationsAndUnlinkPreservesRegistration() throws Exception {
    String token = login("admin");
    long app =
        send(post(APPLICATIONS), token, Map.of("name", "API测试应用", "code", "api-test"), 201)
            .path("id")
            .asLong();
    long api =
        send(
                post(APIS, app),
                token,
                Map.of("operationId", "listCourses", "httpMethod", "GET", "path", "/courses"),
                201)
            .path("id")
            .asLong();
    long first =
        send(
                post(PERMISSIONS, app),
                token,
                Map.of(
                    "name", "菜单一", "code", "course:view", "type", "MENU", "apiIds", List.of(api)),
                201)
            .path("id")
            .asLong();
    send(
        post(PERMISSIONS, app),
        token,
        Map.of("name", "菜单二", "code", "course:edit", "type", "MENU", "apiIds", List.of(api)),
        201);
    send(patch(PERMISSION_ITEM, app, first), token, Map.of("apiIds", List.of()), 200);
    JsonNode result = send(get(APIS, app), token, null, 200);
    assertThat(result.path("items").get(0).path("permissionIds").size()).isEqualTo(1);
  }

  @Test
  @DisplayName("RBAC0：直接调用接口不能绕过权限，停用账号立即失去访问")
  void directRequestsCannotBypassPrivilegesAndDisabledAccountsLoseAccess() throws Exception {
    String admin = login("admin");
    long dept =
        send(
                post(DEPARTMENTS),
                admin,
                json.createObjectNode().put("name", "测试学院").putNull("parentId"),
                201)
            .path("id")
            .asLong();
    JsonNode user =
        send(
            post(USERS),
            admin,
            Map.of(
                "username",
                "teacher",
                "name",
                "教师",
                "departmentId",
                dept,
                "initialPassword",
                "demo1234"),
            201);
    assertThat(user.has("initialPassword")).isFalse();
    long id = user.path("user").path("id").asLong();
    String token = login("teacher");
    send(post(DEPARTMENTS), token, Map.of("name", "非法部门"), 403);
    assertThat(
            send(get(API_PERMISSION, "createDepartment"), token, null, 200)
                .path("allowed")
                .asBoolean())
        .isFalse();
    mvc.perform(get(PERMISSION, "department:create").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.allowed").value(false));
    mvc.perform(get(API_PERMISSION, "missing").header("Authorization", "Bearer " + token))
        .andExpect(status().isNotFound());
    JsonNode failures = send(get(OPERATION_LOGS).param("keyword", "操作失败"), admin, null, 200);
    assertThat(failures.path("items").findValuesAsText("result")).contains("FAILURE");
    assertThat(failures.toString()).doesNotContain("demo1234", token);
    send(patch(USER, id), admin, Map.of("status", "DISABLED"), 200);
    mvc.perform(get(ME).header("Authorization", "Bearer " + token))
        .andExpect(status().isUnauthorized());
    send(
        post(LOGIN),
        null,
        Map.of(
            "username", "teacher", "password", "demo1234", "systems", List.of("permission-center")),
        401);
  }

  @Test
  @DisplayName("验收3：继承角色保存时校验，修改继承关系后我的权限立即变化并留下操作记录")
  void inheritedRolesAreValidatedAndManagePermissionsImmediately() throws Exception {
    String admin = login("admin");
    long app =
        send(post(APPLICATIONS), admin, Map.of("name", "继承端到端", "code", "inheritance-http"), 201)
            .path("id")
            .asLong();
    long menu =
        send(
                post(PERMISSIONS, app),
                admin,
                Map.of("name", "课程菜单", "code", "course:view", "type", "MENU"),
                201)
            .path("id")
            .asLong();
    long button =
        send(
                post(PERMISSIONS, app),
                admin,
                Map.of(
                    "name", "审核课程", "code", "course:review", "type", "BUTTON", "parentId", menu),
                201)
            .path("id")
            .asLong();
    long parent =
        send(post(ROLES), admin, configuration("HTTP父", List.of(menu), List.of()), 201)
            .path("id")
            .asLong();
    long child =
        send(post(ROLES), admin, configuration("HTTP子", List.of(button), List.of(parent)), 201)
            .path("id")
            .asLong();
    send(post(ROLES), admin, Map.of("name", "缺少父角色列表", "permissionIds", List.of()), 400);
    send(post(ROLES), admin, configuration("重复父角色", List.of(), List.of(parent, parent)), 400);
    send(post(ROLES), admin, configuration("继承重复直接配置", List.of(menu), List.of(parent)), 400);
    JsonNode saved = send(get(ROLE, child), admin, null, 200);
    assertThat(saved.path("inheritedRoles").get(0).path("id").asLong()).isEqualTo(parent);
    assertThat(saved.path("effectivePermissionCount").asInt()).isEqualTo(2);
    send(put(ROLE, parent), admin, configuration("HTTP父", List.of(menu), List.of(child)), 400);
    send(put(ROLE, parent), admin, configuration("不应保存的名称", List.of(), List.of()), 400);
    assertThat(send(get(ROLE, parent), admin, null, 200).path("name").asText())
        .isEqualTo("HTTP父");
    long dept =
        send(
                post(DEPARTMENTS),
                admin,
                json.createObjectNode().put("name", "继承学院").putNull("parentId"),
                201)
            .path("id")
            .asLong();
    long user =
        send(
                post(USERS),
                admin,
                Map.of(
                    "username",
                    "inheritance-user",
                    "name",
                    "继承教师",
                    "departmentId",
                    dept,
                    "initialPassword",
                    "demo1234"),
                201)
            .path("user")
            .path("id")
            .asLong();
    send(put(USER_ROLES, user), admin, Map.of("roleIds", List.of(child)), 200);
    String token = login("inheritance-user");
    JsonNode mine = send(get(MY_PERMISSIONS), token, null, 200);
    assertThat(mine.path("businessRoles").findValuesAsText("name")).containsExactly("HTTP子");
    assertThat(mine.toString()).contains("sourceRoles", "HTTP父", "course:review");
    send(delete(ROLE, parent), admin, null, 409);
    send(put(ROLE, child), admin, configuration("HTTP子", List.of(), List.of(parent)), 200);
    send(put(ROLE, parent), admin, configuration("HTTP父", List.of(), List.of()), 200);
    assertThat(send(get(MY_PERMISSIONS), token, null, 200).toString())
        .doesNotContain("course:view");
    JsonNode audit = send(get(OPERATION_LOGS).param("keyword", "继承角色"), admin, null, 200);
    assertThat(audit.path("items").size()).isPositive();
  }

  @Test
  @DisplayName("验收12：审批创建角色名称冲突时申请保持待审核，父角色删除后保留快照且须更换父角色")
  void roleApplicationApprovalRollsBackOnNameConflictAndPreservesParentSnapshot() throws Exception {
    String admin = login("admin");
    long dept =
        send(
                post(DEPARTMENTS),
                admin,
                json.createObjectNode().put("name", "申请学院").putNull("parentId"),
                201)
            .path("id")
            .asLong();
    long managerUser =
        send(
                post(USERS),
                admin,
                Map.of(
                    "username",
                    "proposal-manager",
                    "name",
                    "申请管理员",
                    "departmentId",
                    dept,
                    "initialPassword",
                    "demo1234",
                    "departmentAdministrator",
                    true),
                201)
            .path("user")
            .path("id")
            .asLong();
    String manager = login("proposal-manager");
    long parent =
        send(post(ROLES), admin, configuration("申请父角色", List.of(), List.of()), 201)
            .path("id")
            .asLong();
    long request =
        send(
                post(ROLE_CREATION_REQUESTS),
                manager,
                configuration("HTTP申请角色", List.of(), List.of(parent)),
                201)
            .path("id")
            .asLong();
    JsonNode preview = send(get(ROLE_CREATION_REQUEST, request), manager, null, 200);
    assertThat(preview.has("request")).isFalse();
    assertThat(preview.path("inheritedRoles").get(0).path("exists").asBoolean()).isTrue();
    assertThat(preview.path("effectivePermissions").isArray()).isTrue();
    send(
        post(ROLE_CREATION_REVIEWS, request),
        admin,
        Map.of("decision", "APPROVE", "role", configuration("申请父角色", List.of(), List.of(parent))),
        409);
    assertThat(
            send(get(ROLE_CREATION_REQUEST, request), admin, null, 200)
                .path("status")
                .asText())
        .isEqualTo("PENDING");
    send(delete(ROLE, parent), admin, null, 204);
    JsonNode missing = send(get(ROLE_CREATION_REQUEST, request), manager, null, 200);
    assertThat(missing.path("inheritedRoles").get(0).path("exists").asBoolean()).isFalse();
    assertThat(missing.path("effectivePermissions").isNull()).isTrue();
    send(
        post(ROLE_CREATION_REVIEWS, request),
        admin,
        Map.of(
            "decision", "APPROVE", "role", configuration("HTTP申请角色", List.of(), List.of(parent))),
        400);
    JsonNode approved =
        send(
            post(ROLE_CREATION_REVIEWS, request),
            admin,
            Map.of("decision", "APPROVE", "role", configuration("HTTP申请角色", List.of(), List.of())),
            200);
    assertThat(approved.path("status").asText()).isEqualTo("APPROVED");
    assertThat(approved.path("inheritedRoles").get(0).path("name").asText()).isEqualTo("申请父角色");
    assertThat(send(get(USER, managerUser), admin, null, 200).path("businessRoles").size())
        .isZero();
    long predefined =
        send(get(ROLES).param("roleType", "TOTAL_ADMIN"), admin, null, 200)
            .path("items")
            .get(0)
            .path("id")
            .asLong();
    send(put(ROLE, predefined), admin, configuration("非法修改管理角色", List.of(), List.of()), 403);
    send(get(ROLES).param("roleType", "TOTAL_ADMIN"), manager, null, 403);
    JsonNode restricted = send(get(ROLES), manager, null, 200);
    assertThat(restricted.path("items").findValuesAsText("roleType")).containsOnly("BUSINESS");
    send(post(ROLES), manager, configuration("越权角色", List.of(), List.of()), 403);
  }

  @Test
  @DisplayName("验收14：课程系统凭应用授权逐层查部门、分页选人并按统一身份 ID 查询")
  void queriesIdentityDirectoryWithinTheTargetApplication() throws Exception {
    String admin = login("admin");
    long root = send(post(DEPARTMENTS), admin, json.createObjectNode().put("name", "目录学院").putNull("parentId"), 201).path("id").asLong();
    long child =
        send(post(DEPARTMENTS), admin, Map.of("name", "目录教研室", "parentId", root), 201)
            .path("id").asLong();
    long grandchild =
        send(post(DEPARTMENTS), admin, Map.of("name", "目录教学组", "parentId", child), 201)
            .path("id").asLong();
    long user = createDirectoryUser(admin, "T1008", "目录教师", child);
    long disabled = createDirectoryUser(admin, "T1009", "停用教师", child);
    send(patch(USER, disabled), admin, Map.of("status", "DISABLED"), 200);
    createDirectoryUser(admin, "T1010", "下级教师", grandchild);
    long operator = createDirectoryUser(admin, "directory-operator", "目录操作人", root);
    long application =
        send(post(APPLICATIONS), admin, Map.of("name", "课程管理", "code", COURSE_APPLICATION), 201)
            .path("id").asLong();
    List<Long> apiIds = List.of(
        registerDirectoryApi(admin, application, "listIdentityDepartments", DIRECTORY_DEPARTMENTS),
        registerDirectoryApi(admin, application, "listIdentityDepartmentUsers", DIRECTORY_DEPARTMENT_USERS),
        registerDirectoryApi(admin, application, "lookupIdentityUser", DIRECTORY_USER));
    long permission =
        send(post(PERMISSIONS, application), admin,
                Map.of("name", "课程选人", "code", "directory:read", "type", "MENU", "apiIds", apiIds),
                201).path("id").asLong();
    long role = send(post(ROLES), admin, configuration("课程选人", List.of(permission), List.of()), 201)
        .path("id").asLong();

    String courseToken = login("directory-operator", COURSE_APPLICATION);
    send(get(DIRECTORY_DEPARTMENTS), courseToken, null, 403);
    send(get(DIRECTORY_DEPARTMENTS), null, null, 401);
    send(put(USER_ROLES, operator), admin, Map.of("roleIds", List.of(role)), 200);

    JsonNode roots = send(get(DIRECTORY_DEPARTMENTS).param("keyword", "目录"), courseToken, null, 200);
    assertThat(roots.path("page").asInt()).isEqualTo(1);
    assertThat(roots.path("pageSize").asInt()).isEqualTo(20);
    assertThat(roots.path("total").asLong()).isEqualTo(1);
    JsonNode rootItem = roots.path("items").get(0);
    assertThat(rootItem.path("id").asLong()).isEqualTo(root);
    assertThat(rootItem.path("code").asText()).isEqualTo(Long.toString(root));
    assertThat(rootItem.path("parentId").isNull()).isTrue();
    assertThat(rootItem.path("hasChildren").asBoolean()).isTrue();
    JsonNode children = send(
        get(DIRECTORY_DEPARTMENTS).param("parentId", Long.toString(root)).param("pageSize", "1"),
        courseToken, null, 200);
    assertThat(children.path("items").findValuesAsText("id")).containsExactly(Long.toString(child));
    assertThat(children.path("total").asLong()).isEqualTo(1);
    JsonNode leaf = send(get(DIRECTORY_DEPARTMENTS).param("parentId", Long.toString(child)),
        courseToken, null, 200).path("items").get(0);
    assertThat(leaf.path("hasChildren").asBoolean()).isFalse();

    JsonNode users = send(get(DIRECTORY_DEPARTMENT_USERS, child), courseToken, null, 200);
    assertThat(users.path("total").asLong()).isEqualTo(1);
    JsonNode filtered = send(get(DIRECTORY_DEPARTMENT_USERS, child).param("keyword", "T1008"),
        courseToken, null, 200);
    JsonNode selected = filtered.path("items").get(0);
    assertThat(selected.path("departmentId").asLong()).isEqualTo(child);
    assertThat(selected.path("departmentName").asText()).isEqualTo("目录教研室");
    assertThat(selected.path("userId").asText()).isEqualTo(Long.toString(user));
    assertThat(selected.path("no").asText()).isEqualTo("T1008");
    assertThat(send(get(DIRECTORY_USER, user), courseToken, null, 200))
        .isEqualTo(json.valueToTree(Map.of("userId", Long.toString(user), "no", "T1008", "name", "目录教师")));

    send(get(DIRECTORY_DEPARTMENTS).param("pageSize", "101"), courseToken, null, 400);
    send(get(DIRECTORY_DEPARTMENTS).param("parentId", "999999"), courseToken, null, 404);
    send(get(DIRECTORY_DEPARTMENT_USERS, 999999), courseToken, null, 404);
    send(get(DIRECTORY_USER, 999999), courseToken, null, 404);
    send(get(DIRECTORY_USER, disabled), courseToken, null, 404);
    send(get(DIRECTORY_USER, user).header("X-Application-Code", "permission-center"),
        courseToken, null, 403);
    send(get(DIRECTORY_USER, user), login("directory-operator"), null, 403);
  }

  @Test
  @DisplayName("验收15：放行已配置来源的浏览器预检，实际请求仍校验会话")
  void allowsConfiguredCorsWithoutBypassingAuthentication() throws Exception {
    mvc.perform(options(DIRECTORY_DEPARTMENTS)
            .header(HttpHeaders.ORIGIN, COURSE_ORIGIN)
            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,x-application-code"))
        .andExpect(status().isOk())
        .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, COURSE_ORIGIN));
    mvc.perform(options(LOGIN)
            .header(HttpHeaders.ORIGIN, COURSE_ORIGIN)
            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "content-type"))
        .andExpect(status().isOk())
        .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, COURSE_ORIGIN));
    mvc.perform(get(DIRECTORY_DEPARTMENTS).header(HttpHeaders.ORIGIN, COURSE_ORIGIN))
        .andExpect(status().isUnauthorized())
        .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, COURSE_ORIGIN));
    mvc.perform(options(ME)
            .header(HttpHeaders.ORIGIN, "https://other.test.example")
            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
        .andExpect(status().isForbidden())
        .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
  }

  @Test
  @DisplayName("验收16：登录兼容数值学号，前端权限检查兼容课程短码")
  void acceptsNumericLoginAndCompactCoursePermissionCodes() throws Exception {
    String admin = login("admin");
    long department =
        send(post(DEPARTMENTS), admin, json.createObjectNode().put("name", "兼容性测试部门").putNull("parentId"), 201).path("id").asLong();
    long user = createDirectoryUser(admin, "20220101", "兼容性测试用户", department);
    long application =
        send(post(APPLICATIONS), admin, Map.of("name", "短码兼容测试", "code", "compact-course"), 201)
            .path("id").asLong();
    long permission =
        send(post(PERMISSIONS, application), admin, Map.of("name", "课程查看", "code", "CV", "type", "MENU"),
                201).path("id").asLong();
    long role =
        send(post(ROLES), admin, configuration("短码兼容角色", List.of(permission), List.of()), 201)
            .path("id").asLong();
    send(put(USER_ROLES, user), admin, Map.of("roleIds", List.of(role)), 200);

    String token = login(20220101, "compact-course");
    assertThat(send(get(ME), token, null, 200).path("no").asText()).isEqualTo("20220101");
    JsonNode check = send(get(PERMISSION, "CV"), token, null, 200);
    assertThat(check).isEqualTo(json.valueToTree(Map.of("allowed", true)));
    // 接入系统自定义格式的权限码只做存在性判断，未登记时返回 false 而不是 400。
    assertThat(send(get(PERMISSION, "course.view"), token, null, 200).path("allowed").asBoolean())
        .isFalse();
    send(post(LOGIN), null,
        Map.of("username", true, "password", "demo1234", "systems", List.of("compact-course")), 400);
  }

  @Test
  @DisplayName("验收17：按应用映射直接与继承角色，角色码和权限分别计算")
  // H2 由 JPA 初始化，先还原 V2 前的结构，再执行两套后端共用的迁移。
  @Sql(statements = "ALTER TABLE sys_application DROP COLUMN IF EXISTS role_codes_enabled")
  @Sql("/db/migration/V2__application_role_codes.sql")
  void mapsBusinessRoleCodesForTheTargetApplication() throws Exception {
    String admin = login("admin");
    long course =
        send(post(APPLICATIONS), admin, Map.of("name", "角色映射课程", "code", "mapping-course"), 201)
            .path("id").asLong();
    long library =
        send(post(APPLICATIONS), admin, Map.of("name", "角色映射图书", "code", "mapping-library"), 201)
            .path("id").asLong();
    long legacy =
        send(post(APPLICATIONS), admin, Map.of("name", "角色映射旧应用", "code", "mapping-legacy"), 201)
            .path("id").asLong();
    long coursePermission =
        send(post(PERMISSIONS, course), admin,
                Map.of("name", "添加教师", "code", "TEC", "type", "MENU"), 201)
            .path("id").asLong();
    long legacyPermission =
        send(post(PERMISSIONS, legacy), admin,
                Map.of("name", "旧应用菜单", "code", "legacy:view", "type", "MENU"), 201)
            .path("id").asLong();
    long student = createMappingRole(admin, "课程学生", List.of(), List.of());
    long teacher = createMappingRole(admin, "课程教师", List.of(), List.of());
    long assistant = createMappingRole(admin, "课程助教", List.of(), List.of(teacher));
    long leader = createMappingRole(admin, "课程组长", List.of(), List.of(assistant));
    long courseAdmin = createMappingRole(admin, "课程管理员", List.of(coursePermission), List.of());
    long libraryAdmin = createMappingRole(admin, "图书管理员", List.of(legacyPermission), List.of());
    jdbc.update("UPDATE sys_application SET role_codes_enabled = TRUE WHERE id IN (?, ?)",
        course, library);
    insertRoleCode(course, student, "student");
    insertRoleCode(course, teacher, "teacher");
    insertRoleCode(course, assistant, "teacher");
    insertRoleCode(course, courseAdmin, "admin");
    insertRoleCode(library, libraryAdmin, "admin");

    long department =
        send(post(DEPARTMENTS), admin, json.createObjectNode().put("name", "角色映射部门").putNull("parentId"), 201)
            .path("id").asLong();
    long pupil = createDirectoryUser(admin, "mapping-student", "映射学生", department);
    long operator = createDirectoryUser(admin, "mapping-teacher", "映射教师", department);
    send(put(USER_ROLES, pupil), admin, Map.of("roleIds", List.of(student)), 200);
    send(put(USER_ROLES, operator), admin, Map.of("roleIds", List.of(leader, libraryAdmin)), 200);

    JsonNode pupilIdentity = send(get(ME), login("mapping-student", "mapping-course"), null, 200);
    assertThat(pupilIdentity.path("roles")).isEqualTo(json.valueToTree(List.of("student")));
    assertThat(pupilIdentity.path("permissions")).isEmpty();

    String token = send(post(LOGIN), null, Map.of("username", "mapping-teacher", "password", "demo1234",
        "systems", List.of("mapping-course", "mapping-library", "mapping-legacy")), 200)
        .path("accessToken").asText();
    JsonNode courseIdentity =
        send(get(ME).header("X-Application-Code", "mapping-course"), token, null, 200);
    assertThat(courseIdentity.path("roles")).isEqualTo(json.valueToTree(List.of("teacher")));
    assertThat(courseIdentity.path("permissions")).isEmpty();
    JsonNode libraryIdentity =
        send(get(ME).header("X-Application-Code", "mapping-library"), token, null, 200);
    assertThat(libraryIdentity.path("roles")).isEqualTo(json.valueToTree(List.of("admin")));
    assertThat(libraryIdentity.path("permissions")).isEmpty();
    JsonNode legacyIdentity =
        send(get(ME).header("X-Application-Code", "mapping-legacy"), token, null, 200);
    assertThat(legacyIdentity.path("roles"))
        .isEqualTo(json.valueToTree(List.of(Long.toString(libraryAdmin))));
    assertThat(legacyIdentity.path("permissions"))
        .isEqualTo(json.valueToTree(List.of("legacy:view")));

    send(put(ROLE, teacher), admin, configuration("课程教师改名", List.of(), List.of()), 200);
    send(put(USER_ROLES, operator), admin, Map.of("roleIds", List.of(leader, courseAdmin, libraryAdmin)), 200);
    courseIdentity = send(get(ME).header("X-Application-Code", "mapping-course"), token, null, 200);
    assertThat(courseIdentity.path("roles")).isEqualTo(json.valueToTree(List.of("admin", "teacher")));
    assertThat(courseIdentity.path("permissions")).isEqualTo(json.valueToTree(List.of("TEC")));
    assertThat(send(get(PERMISSION, "TEC").header("X-Application-Code", "mapping-library"),
        token, null, 200).path("allowed").asBoolean()).isFalse();

    jdbc.update("DELETE FROM sys_application_role_code WHERE application_id = ?", course);
    courseIdentity = send(get(ME).header("X-Application-Code", "mapping-course"), token, null, 200);
    assertThat(courseIdentity.path("roles")).isEmpty();
    assertThat(courseIdentity.path("permissions")).isEqualTo(json.valueToTree(List.of("TEC")));
  }

  /** 通过角色管理接口创建用于映射的业务角色。 */
  private long createMappingRole(String admin, String name, List<Long> permissions, List<Long> parents)
      throws Exception {
    return send(post(ROLES), admin, configuration(name, permissions, parents), 201)
        .path("id").asLong();
  }

  /** 模拟运维按应用和业务角色 ID 配置外部角色码。 */
  private void insertRoleCode(long application, long role, String code) {
    jdbc.update("INSERT INTO sys_application_role_code (application_id, role_id, role_code) VALUES (?, ?, ?)",
        application, role, code);
  }

  /** 创建一个有部门的统一身份用户。 */
  private long createDirectoryUser(String admin, String username, String name, long department)
      throws Exception {
    return send(post(USERS), admin, Map.of("username", username, "name", name,
            "departmentId", department, "initialPassword", "demo1234"), 201)
        .path("user").path("id").asLong();
  }

  /** 按真实路由模板登记课程应用的目录 API。 */
  private long registerDirectoryApi(String admin, long application, String operationId, String path)
      throws Exception {
    return send(post(APIS, application), admin,
            Map.of("operationId", operationId, "httpMethod", "GET", "path", path), 201)
        .path("id").asLong();
  }

  @Test
  @DisplayName("验收13：接口契约中的全部 47 个操作都有对应的接口映射")
  void allFortySevenDocumentedOperationsHaveControllerMappings() throws Exception {
    Path document = Path.of(API_DOCUMENT);
    if (!Files.exists(document)) {
      document = Path.of(API_DOCUMENT_FROM_ROOT);
    }
    Map<?, ?> spec = new Yaml().loadAs(Files.readString(document), Map.class);
    Set<String> expected = new HashSet<>();
    Map<?, ?> paths = (Map<?, ?>) spec.get("paths");
    paths.forEach(
        (route, value) ->
            ((Map<?, ?>) value)
                .forEach(
                    (method, operation) -> {
                      if (Set.of("get", "post", "put", "patch", "delete")
                          .contains(method.toString())) {
                        expected.add(method.toString().toUpperCase() + " " + route);
                      }
                    }));
    Set<String> actual = new HashSet<>();
    mappings
        .getHandlerMethods()
        .forEach(
            (mapping, handler) -> {
              for (String route : mapping.getPatternValues()) {
                for (RequestMethod method : mapping.getMethodsCondition().getMethods()) {
                  actual.add(method + " " + route);
                }
              }
            });
    assertThat(expected).hasSize(47);
    assertThat(actual).containsAll(expected);
  }

  /** 角色配置请求体：名称、直接权限和父角色。 */
  private Map<String, Object> configuration(
      String name, List<Long> permissions, List<Long> parents) {
    return Map.of("name", name, "permissionIds", permissions, "inheritedRoleIds", parents);
  }

  /** 用密码 demo1234 登录权限中心，返回访问令牌。 */
  private String login(String username) throws Exception {
    return login(username, "permission-center");
  }

  /** 按目标应用登录，保留请求体中账号的原始 JSON 类型。 */
  private String login(Object username, String application) throws Exception {
    return send(
            post(LOGIN),
            null,
            Map.of(
                "username",
                username,
                "password",
                "demo1234",
                "systems",
                List.of(application)),
            200)
        .path("accessToken")
        .asText();
  }

  /**
   * 发送请求并断言状态码，返回解析后的响应体，响应为空时返回 JSON null。
   *
   * <p>每次响应连同请求方法、匹配的路由模板和状态码写入 target/contract-responses，供契约比对使用。
   *
   * @param request 待发送的请求
   * @param token 访问令牌，为 null 时不带 Authorization 头
   * @param body 请求体，为 null 时不带请求体
   * @param expected 期望的 HTTP 状态码
   * @return 解析后的响应体
   */
  private JsonNode send(
      MockHttpServletRequestBuilder request, String token, Object body, int expected)
      throws Exception {
    if (token != null) {
      request.header("Authorization", "Bearer " + token);
    }
    if (body != null) {
      request.contentType("application/json").content(json.writeValueAsBytes(body));
    }
    MvcResult result = mvc.perform(request).andExpect(status().is(expected)).andReturn();
    String content = result.getResponse().getContentAsString();
    JsonNode response = content.isEmpty() ? json.nullNode() : json.readTree(content);
    Path fixtures = Path.of("target/contract-responses");
    Files.createDirectories(fixtures);
    Files.writeString(
        fixtures.resolve(UUID.randomUUID() + ".json"),
        json.writeValueAsString(
            Map.of(
                "method",
                result.getRequest().getMethod().toLowerCase(),
                "path",
                result.getRequest().getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE),
                "status",
                Integer.toString(expected),
                "body",
                response)));
    return response;
  }
}
