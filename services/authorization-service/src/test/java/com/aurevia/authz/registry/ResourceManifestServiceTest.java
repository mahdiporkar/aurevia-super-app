package com.aurevia.authz.registry;

import static com.aurevia.authz.api.dto.ResourceManifestDtos.*;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ResourceManifestServiceTest {
  private final ResourceManifestService service=
      new ResourceManifestService(mock(ResourceManifestRepository.class),new ObjectMapper());

  @Test void acceptsCanonicalResourceTree() {
    service.validate(manifest(List.of(
      resource("application:hr","APPLICATION",null,List.of("access")),
      resource("module:hr.employee-management","MODULE","application:hr",List.of("access")),
      resource("page:hr.employee.list","PAGE","module:hr.employee-management",List.of("view")),
      resource("component:hr.employee.salary-information","UI_COMPONENT","page:hr.employee.list",List.of("view")),
      resource("field:hr.employee.salary-amount","FIELD","component:hr.employee.salary-information",List.of("view")),
      resource("business:hr.employee","BUSINESS_RESOURCE","module:hr.employee-management",List.of("view","create")),
      new ResourceDefinition("external_resource:hr.workforce-dashboard","EXTERNAL_RESOURCE",
          "module:hr.employee-management","داشبورد","Dashboard","hr","CONFIDENTIAL",
          List.of("view"),null,"APPLICATION_MANIFEST",Map.of(),"SUPERSET","DASHBOARD","127")
    )),"application:hr");
  }

  @Test void rejectsActionButtonAsResource() {
    assertThatThrownBy(()->service.validate(manifest(List.of(
      resource("application:hr","APPLICATION",null,List.of("access")),
      resource("component:hr.employee.create-button","UI_COMPONENT","application:hr",List.of("view"))
    )),"application:hr")).isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("buttons are not resources");
  }

  @Test void rejectsApiUrlAsResourceIdentity() {
    assertThatThrownBy(()->service.validate(manifest(List.of(
      resource("application:hr","APPLICATION",null,List.of("access")),
      resource("business:/api/hr/employees","BUSINESS_RESOURCE","application:hr",List.of("view"))
    )),"application:hr")).isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("API URLs");
  }

  @Test void externalResourceRequiresBinding() {
    assertThatThrownBy(()->service.validate(manifest(List.of(
      resource("application:hr","APPLICATION",null,List.of("access")),
      resource("external_resource:hr.dashboard","EXTERNAL_RESOURCE","application:hr",List.of("view"))
    )),"application:hr")).isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("binding is required");
  }

  private static DefinitionManifest manifest(List<ResourceDefinition> resources) {
    return new DefinitionManifest("hr","1.0.0",resources);
  }

  private static ResourceDefinition resource(String key,String type,String parent,
      List<String> actions) {
    return new ResourceDefinition(key,type,parent,key,key,"hr","INTERNAL",actions,
        "ACTIVE","APPLICATION_MANIFEST",Map.of(),null,null,null);
  }
}
