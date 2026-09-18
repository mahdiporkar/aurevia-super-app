package com.aurevia.bff.proxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class GatewayTargetPolicyTest {
  private final GatewayTargetPolicy policy=new GatewayTargetPolicy(
      "http://operation-gateway:80","https://edge.example.test");

  @Test void resolvesDefaultAndAdditionalApprovedGatewayOrigins() {
    assertThat(policy.resolve("http://operation-gateway:80","/payroll/employees","page=2").toString())
        .isEqualTo("http://operation-gateway/payroll/employees?page=2");
    assertThat(policy.resolve("https://edge.example.test","/service/health",null).toString())
        .isEqualTo("https://edge.example.test/service/health");
  }

  @Test void rejectsUnapprovedOrNonOriginTargets() {
    assertThatThrownBy(()->policy.resolve("https://unapproved.example.test","/employees",null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(()->policy.resolve("https://edge.example.test/base","/employees",null))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
