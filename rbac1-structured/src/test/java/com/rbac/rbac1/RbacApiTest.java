package com.rbac.rbac1;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

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
class RbacApiTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;

  @DynamicPropertySource
  static void signingKeys(DynamicPropertyRegistry registry) throws Exception {
    var generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    var keys = generator.generateKeyPair();
    var directory = Files.createTempDirectory("rbac-test-keys");
    var privateFile = directory.resolve("private.pem");
    var publicFile = directory.resolve("public.pem");
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
  void unifiedLoginMeAndLogoutInvalidateOnlyTheCurrentToken() throws Exception {
    var first = login("admin");
    var second = login("admin");
    mvc.perform(get("/auth/me").header("Authorization", "Bearer " + first))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.no").value("admin"))
        .andExpect(jsonPath("$.managementRole").value("TOTAL_ADMIN"));
    mvc.perform(get("/.well-known/jwks.json"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.keys[0].alg").value("RS256"))
        .andExpect(jsonPath("$.keys[0].d").doesNotExist());
    mvc.perform(post("/auth/logout").header("Authorization", "Bearer " + first))
        .andExpect(status().isNoContent());
    mvc.perform(get("/auth/me").header("Authorization", "Bearer " + first))
        .andExpect(status().isUnauthorized());
    mvc.perform(get("/auth/me").header("Authorization", "Bearer " + second))
        .andExpect(status().isOk());
  }

  @Test
  void apiRegistrySupportsSharedAssociationsAndUnlinkPreservesRegistration() throws Exception {
    var token = login("admin");
    var app =
        send(
                post("/api/v1/applications"),
                token,
                Map.of("name", "API测试应用", "code", "api-test"),
                201)
            .path("id")
            .asLong();
    var api =
        send(
                post("/api/v1/applications/" + app + "/apis"),
                token,
                Map.of("operationId", "listCourses", "httpMethod", "GET", "path", "/courses"),
                201)
            .path("id")
            .asLong();
    var first =
        send(
                post("/api/v1/applications/" + app + "/permissions"),
                token,
                Map.of(
                    "name", "菜单一", "code", "course:view", "type", "MENU", "apiIds", List.of(api)),
                201)
            .path("id")
            .asLong();
    send(
        post("/api/v1/applications/" + app + "/permissions"),
        token,
        Map.of("name", "菜单二", "code", "course:edit", "type", "MENU", "apiIds", List.of(api)),
        201);
    send(
        patch("/api/v1/applications/" + app + "/permissions/" + first),
        token,
        Map.of("apiIds", List.of()),
        200);
    var result = send(get("/api/v1/applications/" + app + "/apis"), token, null, 200);
    assertThat(result.path("items").get(0).path("permissionIds").size()).isEqualTo(1);
  }

  @Test
  void directRequestsCannotBypassPrivilegesAndDisabledAccountsLoseAccess() throws Exception {
    var admin = login("admin");
    var dept =
        send(
                post("/api/v1/departments"),
                admin,
                json.createObjectNode().put("name", "测试学院").putNull("parentId"),
                201)
            .path("id")
            .asLong();
    var user =
        send(
            post("/api/v1/users"),
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
    var id = user.path("user").path("id").asLong();
    var token = login("teacher");
    send(post("/api/v1/departments"), token, Map.of("name", "非法部门"), 403);
    assertThat(
            send(get("/auth/api-permissions/createDepartment"), token, null, 200)
                .path("allowed")
                .asBoolean())
        .isFalse();
    mvc.perform(
            get("/auth/permissions/department:create").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.allowed").value(false));
    mvc.perform(get("/auth/api-permissions/missing").header("Authorization", "Bearer " + token))
        .andExpect(status().isNotFound());
    var failures = send(get("/api/v1/operation-logs").param("keyword", "操作失败"), admin, null, 200);
    assertThat(failures.path("items").findValuesAsText("result")).contains("FAILURE");
    assertThat(failures.toString()).doesNotContain("demo1234", token);
    send(patch("/api/v1/users/" + id), admin, Map.of("status", "DISABLED"), 200);
    mvc.perform(get("/auth/me").header("Authorization", "Bearer " + token))
        .andExpect(status().isUnauthorized());
    send(
        post("/auth/login"),
        null,
        Map.of(
            "username", "teacher", "password", "demo1234", "systems", List.of("permission-center")),
        401);
  }

  @Test
  void inheritedRolesAreValidatedAndManagePermissionsImmediately() throws Exception {
    var admin = login("admin");
    var app =
        send(
                post("/api/v1/applications"),
                admin,
                Map.of("name", "继承端到端", "code", "inheritance-http"),
                201)
            .path("id")
            .asLong();
    var menu =
        send(
                post("/api/v1/applications/" + app + "/permissions"),
                admin,
                Map.of("name", "课程菜单", "code", "course:view", "type", "MENU"),
                201)
            .path("id")
            .asLong();
    var button =
        send(
                post("/api/v1/applications/" + app + "/permissions"),
                admin,
                Map.of("name", "审核课程", "code", "course:review", "type", "BUTTON", "parentId", menu),
                201)
            .path("id")
            .asLong();
    var parent =
        send(post("/api/v1/roles"), admin, configuration("HTTP父", List.of(menu), List.of()), 201)
            .path("id")
            .asLong();
    var child =
        send(
                post("/api/v1/roles"),
                admin,
                configuration("HTTP子", List.of(button), List.of(parent)),
                201)
            .path("id")
            .asLong();
    send(post("/api/v1/roles"), admin, Map.of("name", "缺少父角色列表", "permissionIds", List.of()), 400);
    send(
        post("/api/v1/roles"),
        admin,
        configuration("重复父角色", List.of(), List.of(parent, parent)),
        400);
    send(
        post("/api/v1/roles"),
        admin,
        configuration("继承重复直接配置", List.of(menu), List.of(parent)),
        400);
    var saved = send(get("/api/v1/roles/" + child), admin, null, 200);
    assertThat(saved.path("inheritedRoles").get(0).path("id").asLong()).isEqualTo(parent);
    assertThat(saved.path("effectivePermissionCount").asInt()).isEqualTo(2);
    send(
        put("/api/v1/roles/" + parent),
        admin,
        configuration("HTTP父", List.of(menu), List.of(child)),
        400);
    send(
        put("/api/v1/roles/" + parent), admin, configuration("不应保存的名称", List.of(), List.of()), 400);
    assertThat(send(get("/api/v1/roles/" + parent), admin, null, 200).path("name").asText())
        .isEqualTo("HTTP父");
    var dept =
        send(
                post("/api/v1/departments"),
                admin,
                json.createObjectNode().put("name", "继承学院").putNull("parentId"),
                201)
            .path("id")
            .asLong();
    var user =
        send(
                post("/api/v1/users"),
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
    send(put("/api/v1/users/" + user + "/roles"), admin, Map.of("roleIds", List.of(child)), 200);
    var token = login("inheritance-user");
    var mine = send(get("/api/v1/me/permissions"), token, null, 200);
    assertThat(mine.path("businessRoles").findValuesAsText("name")).containsExactly("HTTP子");
    assertThat(mine.toString()).contains("sourceRoles", "HTTP父", "course:review");
    send(delete("/api/v1/roles/" + parent), admin, null, 409);
    send(
        put("/api/v1/roles/" + child),
        admin,
        configuration("HTTP子", List.of(), List.of(parent)),
        200);
    send(put("/api/v1/roles/" + parent), admin, configuration("HTTP父", List.of(), List.of()), 200);
    assertThat(send(get("/api/v1/me/permissions"), token, null, 200).toString())
        .doesNotContain("course:view");
    var audit = send(get("/api/v1/operation-logs").param("keyword", "继承角色"), admin, null, 200);
    assertThat(audit.path("items").size()).isPositive();
  }

  @Test
  void roleApplicationApprovalRollsBackOnNameConflictAndPreservesParentSnapshot() throws Exception {
    var admin = login("admin");
    var dept =
        send(
                post("/api/v1/departments"),
                admin,
                json.createObjectNode().put("name", "申请学院").putNull("parentId"),
                201)
            .path("id")
            .asLong();
    var managerUser =
        send(
                post("/api/v1/users"),
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
    var manager = login("proposal-manager");
    var parent =
        send(post("/api/v1/roles"), admin, configuration("申请父角色", List.of(), List.of()), 201)
            .path("id")
            .asLong();
    var request =
        send(
                post("/api/v1/role-creation-requests"),
                manager,
                configuration("HTTP申请角色", List.of(), List.of(parent)),
                201)
            .path("id")
            .asLong();
    var preview = send(get("/api/v1/role-creation-requests/" + request), manager, null, 200);
    assertThat(preview.has("request")).isFalse();
    assertThat(preview.path("inheritedRoles").get(0).path("exists").asBoolean()).isTrue();
    assertThat(preview.path("effectivePermissions").isArray()).isTrue();
    send(
        post("/api/v1/role-creation-requests/" + request + "/reviews"),
        admin,
        Map.of("decision", "APPROVE", "role", configuration("申请父角色", List.of(), List.of(parent))),
        409);
    assertThat(
            send(get("/api/v1/role-creation-requests/" + request), admin, null, 200)
                .path("status")
                .asText())
        .isEqualTo("PENDING");
    send(delete("/api/v1/roles/" + parent), admin, null, 204);
    var missing = send(get("/api/v1/role-creation-requests/" + request), manager, null, 200);
    assertThat(missing.path("inheritedRoles").get(0).path("exists").asBoolean()).isFalse();
    assertThat(missing.path("effectivePermissions").isNull()).isTrue();
    send(
        post("/api/v1/role-creation-requests/" + request + "/reviews"),
        admin,
        Map.of(
            "decision", "APPROVE", "role", configuration("HTTP申请角色", List.of(), List.of(parent))),
        400);
    var approved =
        send(
            post("/api/v1/role-creation-requests/" + request + "/reviews"),
            admin,
            Map.of("decision", "APPROVE", "role", configuration("HTTP申请角色", List.of(), List.of())),
            200);
    assertThat(approved.path("status").asText()).isEqualTo("APPROVED");
    assertThat(approved.path("inheritedRoles").get(0).path("name").asText()).isEqualTo("申请父角色");
    assertThat(
            send(get("/api/v1/users/" + managerUser), admin, null, 200)
                .path("businessRoles")
                .size())
        .isZero();
    var predefined =
        send(get("/api/v1/roles").param("roleType", "TOTAL_ADMIN"), admin, null, 200)
            .path("items")
            .get(0)
            .path("id")
            .asLong();
    send(
        put("/api/v1/roles/" + predefined),
        admin,
        configuration("非法修改管理角色", List.of(), List.of()),
        403);
    send(get("/api/v1/roles").param("roleType", "TOTAL_ADMIN"), manager, null, 403);
    var restricted = send(get("/api/v1/roles"), manager, null, 200);
    assertThat(restricted.path("items").findValuesAsText("roleType")).containsOnly("BUSINESS");
    send(post("/api/v1/roles"), manager, configuration("越权角色", List.of(), List.of()), 403);
  }

  private Map<String, Object> configuration(
      String name, List<Long> permissions, List<Long> parents) {
    return Map.of("name", name, "permissionIds", permissions, "inheritedRoleIds", parents);
  }

  @Autowired
  org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping mappings;

  @Test
  void allFortyFourDocumentedOperationsHaveControllerMappings() throws Exception {
    var document = Path.of("../docs/03-api/RBAC1-api.yaml");
    if (!Files.exists(document)) document = Path.of("docs/03-api/RBAC1-api.yaml");
    var spec = new org.yaml.snakeyaml.Yaml().loadAs(Files.readString(document), Map.class);
    var expected = new HashSet<String>();
    var paths = (Map<?, ?>) spec.get("paths");
    paths.forEach(
        (route, value) ->
            ((Map<?, ?>) value)
                .forEach(
                    (method, operation) -> {
                      if (Set.of("get", "post", "put", "patch", "delete")
                          .contains(method.toString()))
                        expected.add(method.toString().toUpperCase() + " " + route);
                    }));
    var actual = new HashSet<String>();
    mappings
        .getHandlerMethods()
        .forEach(
            (mapping, handler) -> {
              for (var route : mapping.getPatternValues())
                for (var method : mapping.getMethodsCondition().getMethods())
                  actual.add(method + " " + route);
            });
    assertThat(expected).hasSize(44);
    assertThat(actual).containsAll(expected);
  }

  private String login(String username) throws Exception {
    return send(
            post("/auth/login"),
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

  private JsonNode send(
      MockHttpServletRequestBuilder request, String token, Object body, int expected)
      throws Exception {
    if (token != null) request.header("Authorization", "Bearer " + token);
    if (body != null) request.contentType("application/json").content(json.writeValueAsBytes(body));
    var result = mvc.perform(request).andExpect(status().is(expected)).andReturn();
    var content = result.getResponse().getContentAsString();
    var response = content.isEmpty() ? json.nullNode() : json.readTree(content);
    var fixtures = Path.of("target/contract-responses");
    Files.createDirectories(fixtures);
    Files.writeString(
        fixtures.resolve(UUID.randomUUID() + ".json"),
        json.writeValueAsString(
            Map.of(
                "method",
                result.getRequest().getMethod().toLowerCase(),
                "path",
                result
                    .getRequest()
                    .getAttribute(
                        org.springframework.web.servlet.HandlerMapping
                            .BEST_MATCHING_PATTERN_ATTRIBUTE),
                "status",
                Integer.toString(expected),
                "body",
                response)));
    return response;
  }
}
