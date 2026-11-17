package com.rbac.rbac1ddd.infrastructure.persistence;

import com.rbac.rbac1ddd.application.role.ApplicationDirectory;
import com.rbac.rbac1ddd.infrastructure.persistence.dao.ApplicationDao;
import com.rbac.rbac1ddd.infrastructure.persistence.po.ApplicationPo;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** 应用名称目录端口的 JPA 实现：从 sys_application 读取应用 ID 与名称的对照。 */
@Repository
@RequiredArgsConstructor
class JpaApplicationDirectory implements ApplicationDirectory {
  private final ApplicationDao applications;

  /** 返回全部应用的 ID → 名称映射。 */
  @Override
  public Map<Long, String> namesById() {
    return applications.findAll().stream()
        .collect(Collectors.toMap(ApplicationPo::getId, ApplicationPo::getName));
  }
}
