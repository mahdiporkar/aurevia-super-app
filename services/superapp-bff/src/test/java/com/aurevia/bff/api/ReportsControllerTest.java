package com.aurevia.bff.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ReportsControllerTest {
  @Test void bindsEveryAuthorizedReportToItsLogicalPublicOperationMapping() {
    List<Map> result=ReportsController.bindToMapping(List.of(Map.of(
        "id","asset-1","url_path","/superset/dashboard/42/")),"public-default");

    assertThat(result).singleElement().satisfies(asset->assertThat(asset.get("url_path"))
        .isEqualTo("/api/v1/superset-instances/public-default/superset/dashboard/42/"));
  }

  @Test void doesNotTurnAnInvalidCatalogUrlIntoATunnelUrl() {
    List<Map> result=ReportsController.bindToMapping(List.of(Map.of(
        "id","asset-1","url_path","https://outside.example/dashboard/42")),"public-default");

    assertThat(result).singleElement().satisfies(asset->assertThat(asset.get("url_path"))
        .isEqualTo("https://outside.example/dashboard/42"));
  }
}
