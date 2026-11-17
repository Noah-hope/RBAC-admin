package com.rbac.rbac1ddd.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.yaml.snakeyaml.Yaml;

/**
 * 两种实现共用的验收测试：逐条移植自 rbac1-structured 的 {@code RbacApiTest}。
 *
 * <p>启动参数、测试数据（test-bootstrap.sql）、启动时创建的总管理员 admin/demo1234 和
 * login、send 辅助方法都与结构化版本相同，断言保持一致，只按 DDD 模块的代码规范调整写法。
 * 结构化版本的 6 个用例已全部移植，包括「接口契约中的 44 个操作都有接口映射」检查。
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
      "rbac.bootstrap.password=demo1234"
    })
@AutoConfigureMockMvc
class SharedAcceptanceTest {

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

  /** 接口契约文件，相对于 rbac1-ddd 模块目录。 */
  private static final String API_DOCUMENT = "../docs/03-api/RBAC1-api.yaml";

  /** 从仓库根目录运行测试时的接口契约文件位置。 */
  private static final String API_DOCUMENT_FROM_ROOT = "docs/03-api/RBAC1-api.yaml";

  @Autowired
  MockMvc mvc;

  @Autowired
  ObjectMapper json;

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
    assertThat(
            send(get(USER, managerUser), admin, null, 200)
                .path("businessRoles")
                .size())
        .isZero();
    long predefined =
        send(get(ROLES).param("roleType", "TOTAL_ADMIN"), admin, null, 200)
            .path("items")
            .get(0)
            .path("id")
            .asLong();
    send(
        put(ROLE, predefined),
        admin,
        configuration("非法修改管理角色", List.of(), List.of()),
        403);
    send(get(ROLES).param("roleType", "TOTAL_ADMIN"), manager, null, 403);
    JsonNode restricted = send(get(ROLES), manager, null, 200);
    assertThat(restricted.path("items").findValuesAsText("roleType")).containsOnly("BUSINESS");
    send(post(ROLES), manager, configuration("越权角色", List.of(), List.of()), 403);
  }

  @Test
  @DisplayName("验收13：接口契约中的全部 44 个操作都有对应的接口映射")
  void allFortyFourDocumentedOperationsHaveControllerMappings() throws Exception {
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
    assertThat(expected).hasSize(44);
    assertThat(actual).containsAll(expected);
  }

  /** 角色配置请求体：名称、直接权限和父角色。 */
  private Map<String, Object> configuration(
      String name, List<Long> permissions, List<Long> parents) {
    return Map.of("name", name, "permissionIds", permissions, "inheritedRoleIds", parents);
  }

  /** 用密码 demo1234 登录权限中心，返回访问令牌。 */
  private String login(String username) throws Exception {
    return send(
            post(LOGIN),
            null,
            Map.of(
                "username",
                username,
                "password",
                "demo1234",
                "systems",
                List.of("permission-center")),
            200)
        .path("accessToken")
        .asText();
  }

  /**
   * 发送请求并断言状态码，返回解析后的响应体。
   *
   * <p>与结构化版本相同，把每次响应写入 target/contract-responses，供契约比对使用。
   *
   * @param token 访问令牌，为 null 时不带 Authorization 头
   * @param body 请求体，为 null 时不带请求体
   * @param expected 期望的 HTTP 状态码
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
