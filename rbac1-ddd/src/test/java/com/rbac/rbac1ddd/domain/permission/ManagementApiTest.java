package com.rbac.rbac1ddd.domain.permission;

import static org.assertj.core.api.Assertions.assertThat;

import com.rbac.rbac1ddd.domain.app.BusinessApplication;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 已登记接口单元测试：不启动 Spring，验证接口调用权限的判断。 */
class ManagementApiTest {

  @Test
  @DisplayName("RBAC0：持有任一关联权限即可调用接口，否则拒绝")
  void 持有任一关联权限即可调用() {
    ManagementApi api = new ManagementApi(1, "course", "listCourses", Set.of(10L, 11L));
    assertThat(api.allows(Set.of(11L, 99L))).isTrue();
    assertThat(api.allows(Set.of(99L))).isFalse();
  }

  @Test
  @DisplayName("RBAC0：权限中心的自助接口对所有已登录用户开放，其他应用的同名接口不开放")
  void 自助接口对所有已登录用户开放() {
    ManagementApi own =
        new ManagementApi(2, BusinessApplication.PERMISSION_CENTER, "getMyPermissions", Set.of());
    ManagementApi elsewhere = new ManagementApi(3, "course", "getMyPermissions", Set.of(10L));
    ManagementApi management =
        new ManagementApi(4, BusinessApplication.PERMISSION_CENTER, "listRoles", Set.of(10L));
    assertThat(own.allows(Set.of())).isTrue();
    assertThat(elsewhere.allows(Set.of())).isFalse();
    assertThat(management.allows(Set.of())).isFalse();
  }
}
