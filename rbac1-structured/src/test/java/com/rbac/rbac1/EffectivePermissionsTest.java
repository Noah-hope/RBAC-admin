package com.rbac.rbac1;

import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.*;

class EffectivePermissionsTest extends InheritanceTestSupport {
  @Test
  void diamondInheritanceDeduplicatesPermissionsAndReportsActualConfiguringRoles() {
    var app=app("course"); var p=permission(app,"course:view",null);
    var a=role("A",p.id); var b=role("B"); var c=role("C"); var d=role("D");
    inherits(b,a); inherits(c,a); inherits(d,b,c);
    var result=new ObjectMapper().valueToTree(service.get(root,d.id));
    assertThat(result.path("permissionIds").size()).isZero();
    assertThat(result.path("effectivePermissions").size()).isEqualTo(1);
    var effective=result.path("effectivePermissions").get(0);
    assertThat(effective.path("acquisition").asText()).isEqualTo("INHERITED");
    assertThat(effective.path("sourceRoles").findValuesAsText("name")).containsExactly("A");
    assertThat(result.path("effectivePermissionCount").asInt()).isEqualTo(1);
    assertThat(result.path("inheritedRoles").findValuesAsText("name")).containsExactly("B","C");
  }
  @Test
  void existingDirectOverlapShowsBothAndKeepsIndependentSources() {
    var app=app("course"); var p=permission(app,"course:view",null);
    var a=role("A",p.id); var b=role("B",p.id); var c=role("C");
    inherits(b,a); inherits(c,b);
    var json=new ObjectMapper();
    var parent=json.valueToTree(service.get(root,b.id));
    assertThat(parent.path("effectivePermissions").size()).isEqualTo(1);
    assertThat(parent.path("effectivePermissions").get(0).path("acquisition").asText()).isEqualTo("BOTH");
    var child=json.valueToTree(service.get(root,c.id));
    assertThat(child.path("effectivePermissions").get(0).path("sourceRoles").findValuesAsText("name"))
        .containsExactly("A","B");
  }
}
