package com.aurevia.authz;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;

/** Executable guardrails for the HTTP/application/persistence boundaries. */
@AnalyzeClasses(packages="com.aurevia.authz",importOptions=ImportOption.DoNotIncludeTests.class)
class ArchitectureBoundariesTest {
  @ArchTest
  static final ArchRule HTTP_API_MUST_NOT_DEPEND_ON_REPOSITORIES = noClasses()
      .that().resideInAPackage("..api..")
      .should().dependOnClassesThat().haveSimpleNameEndingWith("Repository")
      .because("controllers and HTTP DTOs must cross an application-service boundary");

  @ArchTest
  static final ArchRule JDBC_IS_AN_ADAPTER_DETAIL = noClasses()
      .that().haveSimpleNameNotStartingWith("Jdbc")
      .should().dependOnClassesThat().areAssignableTo(JdbcClient.class)
      .because("SQL belongs only in explicit Jdbc repository adapters");

  @ArchTest
  static final ArchRule REPOSITORIES_MUST_REMAIN_PROXYABLE = noClasses()
      .that().areAnnotatedWith(Repository.class)
      .should().haveModifier(JavaModifier.FINAL)
      .because("Spring persistence exception translation may create a class proxy");

  @ArchTest
  static final ArchRule SERVICES_MUST_REMAIN_PROXYABLE = noClasses()
      .that().areAnnotatedWith(Service.class)
      .should().haveModifier(JavaModifier.FINAL)
      .because("transactional and other AOP advice may create a class proxy");
}
