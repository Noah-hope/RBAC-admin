package com.rbac.rbac1ddd.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * 架构测试：检查 DDD 分层的依赖方向，防止领域层和应用层依赖持久化技术。
 */
@AnalyzeClasses(
    packages = "com.rbac.rbac1ddd",
    importOptions = ImportOption.DoNotIncludeTests.class)
class LayerDependencyTest {

  /**
   * 领域层只包含业务规则，不依赖 Spring、JPA、CloneFactory 和其他层。
   */
  @ArchTest
  static final ArchRule DOMAIN_IS_PURE =
      noClasses()
          .that()
          .resideInAPackage("..domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "org.springframework..",
              "jakarta.persistence..",
              "cn.edu.xmu.clonefactory..",
              "..application..",
              "..infrastructure..",
              "..interfaces..");

  /**
   * 应用层只依赖领域层和自己定义的端口，不依赖持久化实现和 JPA。
   */
  @ArchTest
  static final ArchRule APPLICATION_USES_PORTS_ONLY =
      noClasses()
          .that()
          .resideInAPackage("..application..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "..infrastructure..",
              "..interfaces..",
              "jakarta.persistence..",
              "org.springframework.data..");

  /**
   * 领域仓储接口不能出现 Spring Data 或 JPA 类型，只能使用领域对象。
   */
  @ArchTest
  static final ArchRule REPOSITORY_PORTS_USE_DOMAIN_TYPES =
      noClasses()
          .that()
          .resideInAPackage("..domain..")
          .and()
          .haveSimpleNameEndingWith("Repository")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("org.springframework.data..", "jakarta.persistence..");

  /**
   * 接口层不能绕过应用层直接访问持久化实现。
   */
  @ArchTest
  static final ArchRule INTERFACES_DO_NOT_TOUCH_PERSISTENCE =
      noClasses()
          .that()
          .resideInAPackage("..interfaces..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("..infrastructure.persistence..");
}
