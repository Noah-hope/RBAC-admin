package com.rbac.rbac1ddd.domain.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 业务应用聚合单元测试：不启动 Spring，验证登记、编码生成和改名（沿用 RBAC0 规则）。 */
class BusinessApplicationTest {

  @Test
  @DisplayName("RBAC0：登记应用时保留业务系统约定的编码，未填写时生成 app- 开头的编码")
  void 登记应用时保留约定编码或生成编码() {
    BusinessApplication given = BusinessApplication.register("图书管理系统", "library-management");
    BusinessApplication generated = BusinessApplication.register("课程管理系统", null);

    assertThat(given.getId()).isNull();
    assertThat(given.getCode()).isEqualTo("library-management");
    assertThat(given.getName()).isEqualTo("图书管理系统");
    assertThat(generated.getCode())
        .startsWith(BusinessApplication.GENERATED_CODE_PREFIX)
        .hasSize(BusinessApplication.GENERATED_CODE_PREFIX.length() + 36);
    assertThat(BusinessApplication.register("另一个应用", null).getCode())
        .isNotEqualTo(generated.getCode());

    given.assignId(3L);
    assertThat(given.getId()).isEqualTo(3L);
    assertThatThrownBy(() -> given.assignId(4L)).isInstanceOf(IllegalStateException.class);
  }

  @Test
  @DisplayName("RBAC0：修改应用名称只在实际变化时返回 true，编码保持不变")
  void 修改应用名称只在实际变化时返回true() {
    BusinessApplication application = new BusinessApplication(2L, "course", "课程管理系统");

    assertThat(application.rename("课程管理系统")).isFalse();
    assertThat(application.rename("课程项目管理系统")).isTrue();
    assertThat(application.getName()).isEqualTo("课程项目管理系统");
    assertThat(application.getCode()).isEqualTo("course");
  }

  @Test
  @DisplayName("RBAC0：只有编码为 permission-center 的应用是权限中心自身")
  void 只有permissionCenter是权限中心() {
    assertThat(new BusinessApplication(1L, BusinessApplication.PERMISSION_CENTER, "权限管理中心")
            .isPermissionCenter())
        .isTrue();
    assertThat(new BusinessApplication(2L, "course", "课程管理系统").isPermissionCenter()).isFalse();
  }
}
