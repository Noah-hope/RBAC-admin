package com.rbac.rbac1;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rbac.rbac1.entity.BusinessApplication;
import com.rbac.rbac1.entity.Permission;
import com.rbac.rbac1.entity.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 角色有效权限的计算与展示测试：多路径继承去重，并区分直接配置、继承和两者同时存在。
 *
 * <p>把角色详情转成 JSON 后断言，与前端看到的字段保持一致。
 */
class EffectivePermissionsTest extends InheritanceTestSupport {

  @Test
  @DisplayName("验收4：菱形继承时权限只计算一次，来源只列出直接配置该权限的角色，中间传递角色不计为来源")
  void diamondInheritanceDeduplicatesPermissionsAndReportsActualConfiguringRoles() {
    BusinessApplication app = app("course");
    Permission p = permission(app, "course:view", null);
    Role a = role("A", p.getId());
    Role b = role("B");
    Role c = role("C");
    Role d = role("D");
    inherits(b, a);
    inherits(c, a);
    inherits(d, b, c);
    JsonNode result = new ObjectMapper().valueToTree(service.get(root, d.getId()));
    assertThat(result.path("permissionIds").size()).isZero();
    assertThat(result.path("effectivePermissions").size()).isEqualTo(1);
    JsonNode effective = result.path("effectivePermissions").get(0);
    assertThat(effective.path("acquisition").asText()).isEqualTo("INHERITED");
    assertThat(effective.path("sourceRoles").findValuesAsText("name")).containsExactly("A");
    assertThat(result.path("effectivePermissionCount").asInt()).isEqualTo(1);
    assertThat(result.path("inheritedRoles").findValuesAsText("name")).containsExactly("B", "C");
  }

  @Test
  @DisplayName("验收4：直接配置与继承同时存在时标记为 BOTH，子角色列出全部直接配置来源")
  void existingDirectOverlapShowsBothAndKeepsIndependentSources() {
    BusinessApplication app = app("course");
    Permission p = permission(app, "course:view", null);
    Role a = role("A", p.getId());
    Role b = role("B", p.getId());
    Role c = role("C");
    inherits(b, a);
    inherits(c, b);
    ObjectMapper json = new ObjectMapper();
    JsonNode parent = json.valueToTree(service.get(root, b.getId()));
    assertThat(parent.path("effectivePermissions").size()).isEqualTo(1);
    assertThat(parent.path("effectivePermissions").get(0).path("acquisition").asText())
        .isEqualTo("BOTH");
    JsonNode child = json.valueToTree(service.get(root, c.getId()));
    assertThat(
            child.path("effectivePermissions").get(0).path("sourceRoles").findValuesAsText("name"))
        .containsExactly("A", "B");
  }
}
