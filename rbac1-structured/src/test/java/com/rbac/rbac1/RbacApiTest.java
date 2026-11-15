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

  @Autowired
  org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping mappings;

  @Test
  void allFortyFourDocumentedOperationsHaveControllerMappings() throws Exception {
    var document = Path.of("../docs/03-api/openapi.yaml");
    if (!Files.exists(document)) document = Path.of("docs/03-api/openapi.yaml");
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
    return json.readTree(
        mvc.perform(request)
            .andExpect(status().is(expected))
            .andReturn()
            .getResponse()
            .getContentAsString());
  }
}
