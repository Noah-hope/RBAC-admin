package com.rbac.rbac1ddd.interfaces;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 应用、权限与 API 登记接口集成测试，覆盖各业务错误码、数据重复翻译和操作记录。
 *
 * <p>期望值（错误码、中文提示、操作记录文字）与 rbac1-structured 的 ApplicationService、ApiService 相同。
 */
class ApplicationApiTest extends ManagementApiTestSupport {

  /** 应用列表与新增应用。 */
  private static final String APPLICATIONS = "/api/v1/applications";

  /** 修改应用名称。 */
  private static final String APPLICATION = "/api/v1/applications/{applicationId}";

  /** 应用权限树与登记权限。 */
  private static final String PERMISSIONS = "/api/v1/applications/{applicationId}/permissions";

  /** 单个权限的查看和修改。 */
  private static final String PERMISSION =
      "/api/v1/applications/{applicationId}/permissions/{permissionId}";

  /** 应用 API 清单与登记 API。 */
  private static final String APIS = "/api/v1/applications/{applicationId}/apis";

  /** 单个 API 的修改和移除。 */
  private static final String API = "/api/v1/applications/{applicationId}/apis/{apiId}";

  /** 业务系统检查接口权限。 */
  private static final String API_PERMISSION = "/auth/api-permissions/{operationId}";

  /** 数据库重复且没有专门提示时的统一提示，与 rbac1-structured 相同。 */
  private static final String GENERIC_DUPLICATE = "名称、应用标识、接口标识或路由已存在。";

  String adminToken;

  /** 每个测试前登录一个总管理员。 */
  @BeforeEach
  void prepare() {
    long totalAdminRole = id("select id from sys_role where role_type='TOTAL_ADMIN'");
    adminToken = login(user("管理员", null, totalAdminRole));
  }

  // ---------- 应用 ----------

  @Test
  @DisplayName("RBAC0：总管理员分页查看应用，新增应用时可省略编码并写入全局操作记录")
  void 总管理员查看和新增应用() throws Exception {
    call(get(APPLICATIONS).param("page", "1").param("pageSize", "1"), adminToken)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].code").value("permission-center"))
        .andExpect(jsonPath("$.items[0].name").value("权限管理中心"))
        .andExpect(jsonPath("$.page").value(1))
        .andExpect(jsonPath("$.pageSize").value(1));

    String name = uniq("图书");
    String code = uniq("library-");
    JsonNode created =
        read(
            send(post(APPLICATIONS), Map.of("name", name, "code", code))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value(name))
                .andExpect(jsonPath("$.code").value(code)));
    long id = created.get("id").asLong();
    assertThat(globalLog("新增应用", "APPLICATION", id, name)).isEqualTo("登记应用「" + name + "」");

    send(post(APPLICATIONS), Map.of("name", uniq("无编码")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.code").value(org.hamcrest.Matchers.startsWith("app-")));

    long total = count("select count(*) from sys_application");
    call(get(APPLICATIONS).param("pageSize", "100"), adminToken)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(total))
        .andExpect(jsonPath("$.items.length()").value(total));
  }

  @Test
  @DisplayName("RBAC0：修改应用名称不改变编码，名称相同时不写操作记录")
  void 修改应用名称() throws Exception {
    String oldName = uniq("课程");
    long app = application(oldName);
    String newName = uniq("课程项目");

    send(patch(APPLICATION, app), Map.of("name", newName))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(app))
        .andExpect(jsonPath("$.name").value(newName))
        .andExpect(jsonPath("$.code").value(codeOf(oldName)));
    assertThat(globalLog("修改应用", "APPLICATION", app, newName))
        .isEqualTo("名称从「" + oldName + "」改为「" + newName + "」");

    send(patch(APPLICATION, app), Map.of("name", newName)).andExpect(status().isOk());
    assertThat(logCount("修改应用", app)).isEqualTo(1);

    expectProblem(
        send(patch(APPLICATION, 999_999L), Map.of("name", uniq("不存在"))),
        404,
        "NOT_FOUND",
        "应用不存在。");
  }

  @Test
  @DisplayName("RBAC0：应用名称或编码重复时返回 409，并记录失败操作")
  void 应用名称或编码重复() throws Exception {
    String name = uniq("重复应用");
    long app = application(name);
    long other = application(uniq("其他应用"));

    expectDuplicate(send(post(APPLICATIONS), Map.of("name", name)), "该应用名称已存在。");
    expectDuplicate(send(patch(APPLICATION, other), Map.of("name", name)), "该应用名称已存在。");
    expectDuplicate(
        send(post(APPLICATIONS), Map.of("name", uniq("新名称"), "code", codeOf(name))),
        GENERIC_DUPLICATE);
    assertThat(count("select count(*) from sys_application where name=?", name)).isEqualTo(1);
    assertThat(count("select count(*) from sys_application where id=? and name=?", app, name))
        .isEqualTo(1);
    assertThat(failureCount("POST " + APPLICATIONS + "，CONFLICT")).isGreaterThanOrEqualTo(2);
    assertThat(failureCount("PATCH " + APPLICATION + "，CONFLICT")).isGreaterThanOrEqualTo(1);
  }

  @Test
  @DisplayName("RBAC0：应用名称和编码格式不合法时返回 400")
  void 应用请求格式不合法() throws Exception {
    expectProblem(
        send(post(APPLICATIONS), Map.of("name", "非法编码", "code", "Upper")),
        400,
        "INVALID_REQUEST",
        "请检查请求参数及必填字段。");
    expectProblem(
        send(post(APPLICATIONS), Map.of("name", "")), 400, "INVALID_REQUEST", "请检查请求参数及必填字段。");
    expectProblem(
        call(get(APPLICATIONS).param("pageSize", "101"), adminToken),
        400,
        "INVALID_REQUEST",
        "请检查请求参数及必填字段。");
  }

  @Test
  @DisplayName("RBAC0：应用、权限和 API 的管理操作仅限总管理员")
  void 应用权限和API管理仅限总管理员() throws Exception {
    long app = application(uniq("受限应用"));
    long role =
        insert("sys_role", Map.of("name", uniq("应用查看员"), "description", "", "role_type", "BUSINESS"));
    jdbc.update(
        "insert into sys_role_permission(role_id, permission_id) select ?, id from sys_permission"
            + " where code in ('application:view','application:create','application:update',"
            + "'permission:create','permission:update','api:manage')",
        role);
    String token = login(user("应用查看员", null, role));
    String detail = "当前操作仅限总管理员。";

    expectProblem(call(get(APPLICATIONS), token), 403, "FORBIDDEN", detail);
    expectProblem(
        call(json(post(APPLICATIONS), Map.of("name", uniq("越权"))), token), 403, "FORBIDDEN", detail);
    expectProblem(
        call(json(patch(APPLICATION, app), Map.of("name", uniq("越权"))), token),
        403,
        "FORBIDDEN",
        detail);
    expectProblem(call(get(PERMISSIONS, app), token), 403, "FORBIDDEN", detail);
    expectProblem(
        call(json(post(PERMISSIONS, app), menuBody("越权", "a:b")), token), 403, "FORBIDDEN", detail);
    expectProblem(call(get(APIS, app), token), 403, "FORBIDDEN", detail);
    expectProblem(
        call(json(post(APIS, app), apiBody("op", "GET", "/x")), token), 403, "FORBIDDEN", detail);
    assertThat(failureCount("POST " + APIS + "，FORBIDDEN")).isGreaterThanOrEqualTo(1);
  }

  // ---------- 权限 ----------

  @Test
  @DisplayName("RBAC0：登记菜单和按钮权限并关联接口，权限树按菜单展示按钮")
  void 登记菜单和按钮权限() throws Exception {
    String appName = uniq("课程");
    long app = application(appName);
    long listApi = api(app, "listCourses", "GET", "/courses");
    long reviewApi = api(app, "reviewCourse", "POST", "/courses/{courseId}/review");

    Map<String, Object> menu = menuBody("所有课程", "course:viewAll");
    menu.put("apiIds", List.of(listApi));
    JsonNode created =
        read(
            send(post(PERMISSIONS, app), menu)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.applicationId").value(app))
                .andExpect(jsonPath("$.type").value("MENU"))
                .andExpect(jsonPath("$.parentId").doesNotExist())
                .andExpect(jsonPath("$.apis.length()").value(1))
                .andExpect(jsonPath("$.apis[0].id").value(listApi))
                .andExpect(jsonPath("$.apis[0].operationId").value("listCourses")));
    long menuId = created.get("id").asLong();
    assertThat(created.at("/apis/0/permissionIds/0").asLong()).isEqualTo(menuId);
    assertThat(globalLog("新增权限", "PERMISSION", menuId, "所有课程"))
        .isEqualTo("应用「" + appName + "」，权限标识 course:viewAll");

    Map<String, Object> button = buttonBody("审核课程", "course:review", menuId);
    button.put("apiIds", List.of(reviewApi, listApi));
    long buttonId =
        read(
                send(post(PERMISSIONS, app), button)
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.parentId").value(menuId))
                    .andExpect(jsonPath("$.apis[*].id").value(
                        org.hamcrest.Matchers.contains((int) listApi, (int) reviewApi))))
            .get("id")
            .asLong();
    long secondMenu =
        read(send(post(PERMISSIONS, app), menuBody("我的课程", "course:mine"))).get("id").asLong();

    call(get(PERMISSIONS, app), adminToken)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.applicationId").value(app))
        .andExpect(jsonPath("$.applicationName").value(appName))
        .andExpect(jsonPath("$.menus.length()").value(2))
        .andExpect(jsonPath("$.menus[0].id").value(menuId))
        .andExpect(jsonPath("$.menus[0].code").value("course:viewAll"))
        .andExpect(jsonPath("$.menus[0].buttons.length()").value(1))
        .andExpect(jsonPath("$.menus[0].buttons[0].id").value(buttonId))
        .andExpect(jsonPath("$.menus[0].buttons[0].name").value("审核课程"))
        .andExpect(jsonPath("$.menus[1].id").value(secondMenu))
        .andExpect(jsonPath("$.menus[1].buttons.length()").value(0));

    call(get(PERMISSION, app, buttonId), adminToken)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("course:review"))
        .andExpect(jsonPath("$.apis.length()").value(2));
    call(get(APIS, app), adminToken)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].permissionIds").value(
            org.hamcrest.Matchers.contains((int) menuId, (int) buttonId)));
    assertThat(count("select count(*) from sys_role_permission where permission_id=?", menuId))
        .isZero();
  }

  @Test
  @DisplayName("RBAC0：权限中心新登记的权限自动授予总管理员")
  void 权限中心新登记的权限自动授予总管理员() throws Exception {
    long center = id("select id from sys_application where code='permission-center'");
    long menuId =
        read(send(post(PERMISSIONS, center), menuBody("新管理菜单", uniq("center:view"))))
            .get("id")
            .asLong();

    assertThat(
            count(
                "select count(*) from sys_role_permission rp join sys_role r on r.id=rp.role_id"
                    + " where r.role_type='TOTAL_ADMIN' and rp.permission_id=?",
                menuId))
        .isEqualTo(1);
  }

  @Test
  @DisplayName("RBAC0：菜单不能有上级，按钮的上级必须是同一应用的菜单，应用不存在时返回 404")
  void 登记权限的上下级规则() throws Exception {
    long app = application(uniq("课程"));
    long other = application(uniq("图书"));
    long menuId = read(send(post(PERMISSIONS, app), menuBody("课程", "course:view"))).get("id").asLong();
    long buttonId =
        read(send(post(PERMISSIONS, app), buttonBody("新增", "course:create", menuId)))
            .get("id")
            .asLong();
    long foreignMenu =
        read(send(post(PERMISSIONS, other), menuBody("图书", "book:view"))).get("id").asLong();

    Map<String, Object> menuWithParent = menuBody("子菜单", "course:sub");
    menuWithParent.put("parentId", menuId);
    expectProblem(
        send(post(PERMISSIONS, app), menuWithParent), 400, "INVALID_REQUEST", "菜单权限的上级必须为空。");
    String buttonDetail = "按钮权限的上级必须是同一应用的菜单。";
    Map<String, Object> orphan = menuBody("孤立按钮", "course:orphan");
    orphan.put("type", "BUTTON");
    expectProblem(send(post(PERMISSIONS, app), orphan), 400, "INVALID_REQUEST", buttonDetail);
    expectProblem(
        send(post(PERMISSIONS, app), buttonBody("按钮下按钮", "course:x", buttonId)),
        400,
        "INVALID_REQUEST",
        buttonDetail);
    expectProblem(
        send(post(PERMISSIONS, app), buttonBody("跨应用", "course:y", foreignMenu)),
        400,
        "INVALID_REQUEST",
        buttonDetail);
    expectProblem(
        send(post(PERMISSIONS, 999_999L), menuBody("不存在", "x:y")), 404, "NOT_FOUND", "应用不存在。");
    expectProblem(call(get(PERMISSIONS, 999_999L), adminToken), 404, "NOT_FOUND", "应用不存在。");
    expectProblem(
        send(post(PERMISSIONS, app), menuBody("格式", "no-colon")),
        400,
        "INVALID_REQUEST",
        "请检查请求参数及必填字段。");
  }

  @Test
  @DisplayName("RBAC0：同一应用内权限标识唯一，不同应用可以使用相同标识")
  void 同一应用内权限标识唯一() throws Exception {
    long app = application(uniq("课程"));
    long other = application(uniq("图书"));
    send(post(PERMISSIONS, app), menuBody("课程", "course:view")).andExpect(status().isCreated());
    send(post(PERMISSIONS, other), menuBody("课程", "course:view")).andExpect(status().isCreated());
    long second =
        read(send(post(PERMISSIONS, app), menuBody("课程二", "course:edit"))).get("id").asLong();

    expectDuplicate(
        send(post(PERMISSIONS, app), menuBody("课程三", "course:view")), "该应用内已存在相同的权限标识。");
    expectDuplicate(
        send(patch(PERMISSION, app, second), Map.of("code", "course:view")),
        "该应用内已存在相同的权限标识。");
    assertThat(count("select count(*) from sys_permission where id=? and code='course:edit'", second))
        .isEqualTo(1);
    assertThat(failureCount("POST " + PERMISSIONS + "，CONFLICT")).isGreaterThanOrEqualTo(1);
  }

  @Test
  @DisplayName("RBAC0：权限只能关联同一应用下已登记的 API，失败时不保存权限")
  void 权限只能关联同一应用下已登记的API() throws Exception {
    long app = application(uniq("课程"));
    long other = application(uniq("图书"));
    long foreignApi = api(other, "listBooks", "GET", "/books");
    String detail = "请选择同一应用下已登记的API。";

    Map<String, Object> foreign = menuBody("跨应用", "course:view");
    foreign.put("apiIds", List.of(foreignApi));
    expectProblem(send(post(PERMISSIONS, app), foreign), 400, "INVALID_REQUEST", detail);
    Map<String, Object> missing = menuBody("未登记", "course:view");
    missing.put("apiIds", List.of(999_999L));
    expectProblem(send(post(PERMISSIONS, app), missing), 400, "INVALID_REQUEST", detail);
    assertThat(count("select count(*) from sys_permission where application_id=?", app)).isZero();

    long menuId = read(send(post(PERMISSIONS, app), menuBody("课程", "course:view"))).get("id").asLong();
    expectProblem(
        send(patch(PERMISSION, app, menuId), Map.of("apiIds", List.of(foreignApi))),
        400,
        "INVALID_REQUEST",
        detail);
    Map<String, Object> repeated = menuBody("重复", "course:dup");
    repeated.put("apiIds", List.of(foreignApi, foreignApi));
    expectProblem(
        send(post(PERMISSIONS, app), repeated), 400, "INVALID_REQUEST", "请检查请求参数及必填字段。");
  }

  @Test
  @DisplayName("RBAC0：修改权限名称、标识和关联 API，空请求返回 400，类型和所属菜单不变")
  void 修改权限() throws Exception {
    String appName = uniq("课程");
    long app = application(appName);
    long api = api(app, "listCourses", "GET", "/courses");
    long menuId = read(send(post(PERMISSIONS, app), menuBody("课程", "course:view"))).get("id").asLong();

    send(patch(PERMISSION, app, menuId), Map.of("name", "课程列表", "code", "course:list", "apiIds", List.of(api)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("课程列表"))
        .andExpect(jsonPath("$.code").value("course:list"))
        .andExpect(jsonPath("$.type").value("MENU"))
        .andExpect(jsonPath("$.apis[0].id").value(api));
    assertThat(globalLog("修改权限", "PERMISSION", menuId, "课程列表"))
        .isEqualTo(
            "应用「" + appName + "」，更新API关联，名称从「课程」改为「课程列表」，标识从 course:view 改为 course:list");

    send(patch(PERMISSION, app, menuId), Map.of("name", "课程列表")).andExpect(status().isOk());
    assertThat(logCount("修改权限", menuId)).isEqualTo(1);

    expectProblem(
        send(patch(PERMISSION, app, menuId), Map.of()),
        400,
        "INVALID_REQUEST",
        "请填写要修改的权限名称或标识。");
    Map<String, Object> nullName = new HashMap<>();
    nullName.put("name", null);
    expectProblem(
        send(patch(PERMISSION, app, menuId), nullName),
        400,
        "INVALID_REQUEST",
        "请检查请求参数及必填字段。");
    expectProblem(
        send(patch(PERMISSION, app, 999_999L), Map.of("name", "不存在")),
        404,
        "NOT_FOUND",
        "该应用下不存在此权限。");
    expectProblem(
        send(patch(PERMISSION, 999_999L, menuId), Map.of("name", "不存在")),
        404,
        "NOT_FOUND",
        "应用不存在。");
  }

  @Test
  @DisplayName("RBAC0：查看权限只返回指定应用下的权限")
  void 查看权限只返回指定应用下的权限() throws Exception {
    long app = application(uniq("课程"));
    long other = application(uniq("图书"));
    long menuId = read(send(post(PERMISSIONS, app), menuBody("课程", "course:view"))).get("id").asLong();

    call(get(PERMISSION, app, menuId), adminToken)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(menuId))
        .andExpect(jsonPath("$.apis.length()").value(0));
    expectProblem(call(get(PERMISSION, other, menuId), adminToken), 404, "NOT_FOUND", "权限不存在。");
    expectProblem(call(get(PERMISSION, app, 999_999L), adminToken), 404, "NOT_FOUND", "权限不存在。");
  }

  // ---------- API ----------

  @Test
  @DisplayName("RBAC0：登记、查询和编辑 API，写入全局操作记录")
  void 登记查询和编辑API() throws Exception {
    long app = application(uniq("课程"));
    JsonNode created =
        read(
            send(post(APIS, app), apiBody("listCourses", "GET", "/courses"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.applicationId").value(app))
                .andExpect(jsonPath("$.operationId").value("listCourses"))
                .andExpect(jsonPath("$.httpMethod").value("GET"))
                .andExpect(jsonPath("$.path").value("/courses"))
                .andExpect(jsonPath("$.permissionIds.length()").value(0)));
    long first = created.get("id").asLong();
    assertThat(globalLog("登记API", "API", first, "listCourses")).isEqualTo("GET /courses");
    long second = api(app, "createCourse", "POST", "/courses");
    api(app, "listTerms", "GET", "/terms");

    call(get(APIS, app).param("keyword", "Course"), adminToken)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(2))
        .andExpect(jsonPath("$.items[*].id").value(
            org.hamcrest.Matchers.contains((int) first, (int) second)));
    call(get(APIS, app).param("keyword", "/terms"), adminToken)
        .andExpect(jsonPath("$.total").value(1));
    call(get(APIS, app).param("page", "2").param("pageSize", "2"), adminToken)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(3))
        .andExpect(jsonPath("$.page").value(2))
        .andExpect(jsonPath("$.items.length()").value(1));

    send(patch(API, app, second), Map.of("path", "/courses/new"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.operationId").value("createCourse"))
        .andExpect(jsonPath("$.httpMethod").value("POST"))
        .andExpect(jsonPath("$.path").value("/courses/new"));
    assertThat(globalLog("编辑API", "API", second, "createCourse")).isEqualTo("POST /courses/new");

    expectProblem(
        send(patch(API, app, second), Map.of()), 400, "INVALID_REQUEST", "请填写要修改的接口资料。");
    expectProblem(
        send(patch(API, app, 999_999L), Map.of("path", "/x")), 404, "NOT_FOUND", "API不存在。");
    expectProblem(call(get(APIS, 999_999L), adminToken), 404, "NOT_FOUND", "应用不存在。");
    expectProblem(
        send(post(APIS, 999_999L), apiBody("op", "GET", "/x")), 404, "NOT_FOUND", "应用不存在。");
    expectProblem(
        send(post(APIS, app), apiBody("op", "FETCH", "/x")),
        400,
        "INVALID_REQUEST",
        "请检查请求参数及必填字段。");
  }

  @Test
  @DisplayName("RBAC0：同一应用内 operationId 唯一，请求方法与路径的组合唯一")
  void 同一应用内API唯一() throws Exception {
    long app = application(uniq("课程"));
    long other = application(uniq("图书"));
    api(app, "listCourses", "GET", "/courses");
    long second = api(app, "createCourse", "POST", "/courses");
    api(other, "listCourses", "GET", "/courses");

    expectDuplicate(send(post(APIS, app), apiBody("listCourses", "GET", "/all")), GENERIC_DUPLICATE);
    expectDuplicate(send(post(APIS, app), apiBody("allCourses", "GET", "/courses")), GENERIC_DUPLICATE);
    expectDuplicate(send(patch(API, app, second), Map.of("httpMethod", "GET")), GENERIC_DUPLICATE);
    assertThat(count("select count(*) from sys_api where application_id=?", app)).isEqualTo(2);
    assertThat(failureCount("POST " + APIS + "，CONFLICT")).isGreaterThanOrEqualTo(2);
    assertThat(failureCount("PATCH " + API + "，CONFLICT")).isGreaterThanOrEqualTo(1);
  }

  @Test
  @DisplayName("RBAC0：移除 API 只删除它的权限关联，权限保留，之后针对该接口的检查返回 404")
  void 移除API只删除权限关联() throws Exception {
    String code = uniq("course-");
    long app =
        read(send(post(APPLICATIONS), Map.of("name", uniq("课程"), "code", code))).get("id").asLong();
    long api = api(app, "listCourses", "GET", "/courses");
    Map<String, Object> menu = menuBody("课程", "course:view");
    menu.put("apiIds", List.of(api));
    long menuId = read(send(post(PERMISSIONS, app), menu)).get("id").asLong();

    long role =
        insert("sys_role", Map.of("name", uniq("教师"), "description", "", "role_type", "BUSINESS"));
    jdbc.update("insert into sys_role_permission(role_id, permission_id) values (?, ?)", role, menuId);
    long teacher = user("教师", null, role);
    String teacherToken = token(teacher, List.of(code));
    session(teacherToken, teacher);
    call(get(API_PERMISSION, "listCourses"), teacherToken)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.allowed").value(true));

    call(delete(API, app, api), adminToken).andExpect(status().isNoContent());
    assertThat(count("select count(*) from sys_api where id=?", api)).isZero();
    assertThat(count("select count(*) from sys_api_permission where api_id=?", api)).isZero();
    assertThat(count("select count(*) from sys_permission where id=?", menuId)).isEqualTo(1);
    assertThat(globalLog("移除API", "API", api, "listCourses")).isEqualTo("移除接口登记及全部权限关联");
    call(get(PERMISSION, app, menuId), adminToken).andExpect(jsonPath("$.apis.length()").value(0));
    call(get(API_PERMISSION, "listCourses"), teacherToken).andExpect(status().isNotFound());

    expectProblem(call(delete(API, app, api), adminToken), 404, "NOT_FOUND", "API不存在。");
  }

  // ---------- 辅助方法 ----------

  /** 以总管理员身份发送 JSON 请求。 */
  private ResultActions send(MockHttpServletRequestBuilder request, Object content)
      throws Exception {
    return call(json(request, content), adminToken);
  }

  /** 设置 JSON 请求体。 */
  private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, Object content)
      throws Exception {
    return request.contentType(MediaType.APPLICATION_JSON).content(body(content));
  }

  /** 读取响应体。 */
  private JsonNode read(ResultActions result) throws Exception {
    return json.readTree(result.andReturn().getResponse().getContentAsString());
  }

  /** 通过接口新增应用，编码由名称推导（见 {@link #codeOf}），返回应用 ID。 */
  private long application(String name) throws Exception {
    return read(
            send(post(APPLICATIONS), Map.of("name", name, "code", codeOf(name)))
                .andExpect(status().isCreated()))
        .get("id")
        .asLong();
  }

  /** 由应用名称推导出符合格式要求的应用编码；测试中的应用名称各不相同，编码也就各不相同。 */
  private static String codeOf(String name) {
    return "code-" + Integer.toUnsignedString(name.hashCode());
  }

  /** 通过接口登记 API，返回 API ID。 */
  private long api(long app, String operationId, String method, String path) throws Exception {
    return read(send(post(APIS, app), apiBody(operationId, method, path)).andExpect(status().isCreated()))
        .get("id")
        .asLong();
  }

  /** 登记 API 的请求体。 */
  private static Map<String, Object> apiBody(String operationId, String method, String path) {
    return Map.of("operationId", operationId, "httpMethod", method, "path", path);
  }

  /** 登记菜单的请求体，可继续放入 apiIds 或 parentId。 */
  private static Map<String, Object> menuBody(String name, String code) {
    return new HashMap<>(Map.of("name", name, "code", code, "type", "MENU"));
  }

  /** 登记按钮的请求体。 */
  private static Map<String, Object> buttonBody(String name, String code, long parentId) {
    return new HashMap<>(Map.of("name", name, "code", code, "type", "BUTTON", "parentId", parentId));
  }

  /** 断言 problem+json 的状态码、错误码和中文提示。 */
  private static void expectProblem(ResultActions result, int status, String code, String detail)
      throws Exception {
    result
        .andExpect(status().is(status))
        .andExpect(jsonPath("$.code").value(code))
        .andExpect(jsonPath("$.detail").value(detail));
  }

  /** 断言数据重复返回 409「数据已存在」。 */
  private static void expectDuplicate(ResultActions result, String detail) throws Exception {
    expectProblem(result, 409, "CONFLICT", detail);
    result.andExpect(jsonPath("$.title").value("数据已存在"));
  }

  /** 查询唯一一条全局成功记录的内容，并核对记录对象。 */
  private String globalLog(String action, String targetType, long targetId, String targetName) {
    return jdbc.queryForObject(
        "select l.detail from sys_operation_log l join sys_operation_log_target t on t.operation_log_id=l.id"
            + " where l.action=? and l.scope='GLOBAL' and l.result='SUCCESS' and t.target_type=?"
            + " and t.target_id=? and t.target_name=? and t.department_id_at_event is null"
            + " order by l.id desc limit 1",
        String.class,
        action,
        targetType,
        targetId,
        targetName);
  }

  /** 统计指定动作针对某对象的操作记录条数。 */
  private long logCount(String action, long targetId) {
    return count(
        "select count(*) from sys_operation_log l join sys_operation_log_target t on t.operation_log_id=l.id"
            + " where l.action=? and t.target_id=?",
        action,
        targetId);
  }

  /** 统计失败操作记录条数。 */
  private long failureCount(String detail) {
    return count(
        "select count(*) from sys_operation_log where result='FAILURE' and detail=?", detail);
  }
}
