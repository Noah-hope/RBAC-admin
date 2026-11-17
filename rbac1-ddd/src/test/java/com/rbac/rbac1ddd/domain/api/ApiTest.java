package com.rbac.rbac1ddd.domain.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 接口登记聚合单元测试：不启动 Spring，验证登记、部分修改和所属应用判断（沿用 RBAC0 规则）。 */
class ApiTest {

  @Test
  @DisplayName("RBAC0：登记接口后回填 ID，接口描述为「请求方法 路由」")
  void 登记接口后回填ID() {
    Api api = Api.register(2L, "listCourses", "GET", "/courses");

    assertThat(api.getId()).isNull();
    api.assignId(7L);

    assertThat(api.getId()).isEqualTo(7L);
    assertThat(api.getApplicationId()).isEqualTo(2L);
    assertThat(api.route()).isEqualTo("GET /courses");
    assertThatThrownBy(() -> api.assignId(8L)).isInstanceOf(IllegalStateException.class);
  }

  @Test
  @DisplayName("RBAC0：修改接口资料只修改填写的项，所属应用不变")
  void 修改接口资料只修改填写的项() {
    Api api = new Api(7L, 2L, "listCourses", "GET", "/courses");

    api.revise(null, "POST", null);
    assertThat(api.getOperationId()).isEqualTo("listCourses");
    assertThat(api.getHttpMethod()).isEqualTo("POST");
    assertThat(api.getPath()).isEqualTo("/courses");

    api.revise("createCourse", null, "/courses/{courseId}");
    assertThat(api.getOperationId()).isEqualTo("createCourse");
    assertThat(api.getHttpMethod()).isEqualTo("POST");
    assertThat(api.getPath()).isEqualTo("/courses/{courseId}");
    assertThat(api.getApplicationId()).isEqualTo(2L);
  }

  @Test
  @DisplayName("RBAC0：接口只属于登记它的应用")
  void 接口只属于登记它的应用() {
    Api api = new Api(7L, 2L, "listCourses", "GET", "/courses");

    assertThat(api.belongsTo(2L)).isTrue();
    assertThat(api.belongsTo(3L)).isFalse();
  }
}
