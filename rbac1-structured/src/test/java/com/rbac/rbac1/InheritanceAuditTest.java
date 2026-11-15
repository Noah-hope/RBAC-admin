package com.rbac.rbac1;

import static org.assertj.core.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;

class InheritanceAuditTest extends InheritanceTestSupport {
  @Test
  void inheritanceChangesRetainReadableParentNamesInTheAuditSnapshot() {
    var parent = role("课程查看员");
    var child = role("课程教师");
    service.update(root, child.id, input("课程教师", List.of(), List.of(parent.id)));
    service.update(root, parent.id, input("已改名", List.of(), List.of()));
    assertThat(logs.findAll())
        .anySatisfy(
            log -> {
              assertThat(log.action).isEqualTo("修改角色");
              assertThat(log.detail).contains("继承角色", "课程查看员");
              assertThat(log.scope).isEqualTo("GLOBAL");
              assertThat(log.result).isEqualTo("SUCCESS");
            });
  }
}
