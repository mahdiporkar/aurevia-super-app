package com.aurevia.bff.docs;

import com.aurevia.bff.api.IdentityProviderLoginController;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.junit.jupiter.api.Test;
import org.springframework.web.method.HandlerMethod;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class BffOpenApiSecurityTest {
  @Test
  void catchAllMutationsRequireSessionAndCsrfTogether() {
    var config = new BffOpenApiConfiguration();
    var api = config.bffOpenApi().paths(new Paths()
        .addPathItem("/api/v1/admin/{path}", new PathItem().get(new Operation()).post(new Operation()))
        .addPathItem("/{panelSlug}/{path}", new PathItem().patch(new Operation()).delete(new Operation()))
        .addPathItem("/api/v1/superset/{path}", new PathItem().post(new Operation())));
    config.bffRequestSecurityDocumentation().customise(api);
    assertThat(api.getPaths().get("/api/v1/admin/{path}").getPost().getSecurity())
        .singleElement().satisfies(security -> assertThat(security).containsKeys("browserSession", "csrfToken"));
    assertThat(api.getPaths().get("/{panelSlug}/{path}").getPatch().getSecurity())
        .singleElement().satisfies(security -> assertThat(security).containsKey("csrfToken"));
    assertThat(api.getPaths().get("/{panelSlug}/{path}").getDelete().getSecurity())
        .singleElement().satisfies(security -> assertThat(security).containsKey("csrfToken"));
    assertThat(api.getPaths().get("/api/v1/admin/{path}").getGet().getSecurity()).isNull();
    assertThat(api.getPaths().get("/api/v1/superset/{path}").getPost().getSecurity()).isNull();
  }

  @Test
  void loginDocumentationIsAnonymousAndDescribesRedirect() throws Exception {
    var controller = mock(IdentityProviderLoginController.class);
    var handler = new HandlerMethod(controller, IdentityProviderLoginController.class
        .getMethod("login", String.class, String.class, String.class));
    var operation = new Operation().responses(new ApiResponses().addApiResponse("200", new ApiResponse()));
    new BffOpenApiConfiguration().bffOperationDocumentation().customize(operation, handler);
    assertThat(operation.getSecurity()).isEmpty();
    assertThat(operation.getResponses()).containsKey("302").doesNotContainKey("200");
  }
}
