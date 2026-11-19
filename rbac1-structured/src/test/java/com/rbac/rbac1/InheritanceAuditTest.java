package com.rbac.rbac1;

import static org.assertj.core.api.Assertions.assertThat;

import com.rbac.rbac1.entity.Role;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 继承关系变更的操作记录测试：记录中保留父角色当时的名称，父角色改名后仍可读。 */
class InheritanceAuditTest extends InheritanceTestSupport {

  @Test
  @DisplayName("验收3：修改继承关系留下全局操作记录，父角色改名后记录中仍保留原名称")
  void inheritanceChangesRetainReadableParentNamesInTheAuditSnapshot() {
    Role parent = role("课程查看员");
    Role child = role("课程教师");
    service.update(root, child.getId(), input("课程教师", List.of(), List.of(parent.getId())));
    service.update(root, parent.getId(), input("已改名", List.of(), List.of()));
    assertThat(logs.findAll())
        .anySatisfy(
            log -> {
              assertThat(log.getAction()).isEqualTo("修改角色");
              assertThat(log.getDetail()).contains("继承角色", "课程查看员");
              assertThat(log.getScope()).isEqualTo("GLOBAL");
              assertThat(log.getResult()).isEqualTo("SUCCESS");
            });
  }
}
