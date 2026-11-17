package com.rbac.rbac1ddd.application.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rbac.rbac1ddd.domain.shared.DomainException;
import com.rbac.rbac1ddd.domain.shared.DomainException.Reason;
import com.rbac.rbac1ddd.domain.user.ManagementRole;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 登录会话单元测试：不启动 Spring，验证按令牌受众确定目标应用。 */
class AuthenticatedSessionTest {

  /** 创建令牌受众为给定应用的登录会话。 */
  private static AuthenticatedSession session(String... audience) {
    return new AuthenticatedSession(
        new Operator(1, "zhang", "张三", null, ManagementRole.NONE), List.of(audience));
  }

  @Test
  @DisplayName("RBAC0：未指定目标应用时使用令牌唯一的受众")
  void 未指定目标应用时使用唯一受众() {
    assertThat(session("course").applicationContext(null)).isEqualTo("course");
    assertThat(session("course", "permission-center").applicationContext("course"))
        .isEqualTo("course");
  }

  @Test
  @DisplayName("RBAC0：令牌适用多个应用时必须指定目标应用")
  void 多个受众时必须指定目标应用() {
    assertThatThrownBy(() -> session("course", "permission-center").applicationContext(null))
        .isInstanceOfSatisfying(
            DomainException.class,
            error -> {
              assertThat(error.reason()).isEqualTo(Reason.INVALID_REQUEST);
              assertThat(error.getMessage()).isEqualTo("请指定目标应用。");
            });
  }

  @Test
  @DisplayName("RBAC0：令牌不适用于目标应用时拒绝")
  void 令牌不适用于目标应用时拒绝() {
    assertThatThrownBy(() -> session("course").applicationContext("permission-center"))
        .isInstanceOfSatisfying(
            DomainException.class,
            error -> {
              assertThat(error.reason()).isEqualTo(Reason.APPLICATION_MISMATCH);
              assertThat(error.getMessage()).isEqualTo("令牌不适用于该应用。");
            });
  }
}
