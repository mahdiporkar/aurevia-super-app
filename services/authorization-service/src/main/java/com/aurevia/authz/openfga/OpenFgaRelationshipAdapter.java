package com.aurevia.authz.openfga;

import dev.openfga.sdk.api.client.OpenFgaClient;
import dev.openfga.sdk.api.client.model.ClientCheckRequest;
import org.springframework.stereotype.Component;

@Component
class OpenFgaRelationshipAdapter implements RelationshipAuthorizationPort {
  private final OpenFgaClient client;
  OpenFgaRelationshipAdapter(OpenFgaClient client) { this.client = client; }
  @Override public boolean check(String user, String relation, String object) {
    try {
      var response = client.check(new ClientCheckRequest().user(user).relation(relation)._object(object)).get();
      return Boolean.TRUE.equals(response.getAllowed());
    } catch (Exception unavailable) {
      // Availability, malformed model, timeout, and missing context all fail closed.
      return false;
    }
  }
}
