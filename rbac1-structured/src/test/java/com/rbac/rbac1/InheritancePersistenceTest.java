package com.rbac.rbac1;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import jakarta.persistence.EntityManager;

@DataJpaTest(properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop"}, showSql=false)
class InheritancePersistenceTest {
  @Autowired EntityManager entityManager;

  @Test
  void inheritanceAndApplicationParentsHaveSeparateStorage() {
    var tables = entityManager.createNativeQuery(
        "select table_name from information_schema.tables where table_schema='PUBLIC'", String.class)
        .getResultList();
    assertThat(tables).contains("SYS_ROLE_INHERITANCE", "SYS_ROLE_CREATE_REQUEST_INHERITANCE");
  }
}
