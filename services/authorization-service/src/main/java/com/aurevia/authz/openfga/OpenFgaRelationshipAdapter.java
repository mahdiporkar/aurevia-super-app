package com.aurevia.authz.openfga;

import dev.openfga.sdk.api.client.OpenFgaClient;
import dev.openfga.sdk.api.client.model.ClientCheckRequest;
import dev.openfga.sdk.api.client.model.ClientTupleKey;
import dev.openfga.sdk.api.client.model.ClientTupleKeyWithoutCondition;
import java.util.List;
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

  @Override
  public void write(String user, String relation, String object) {
    try {
      client.writeTuples(List.of(new ClientTupleKey()
          .user(user).relation(relation)._object(object))).get();
    } catch (Exception failure) {
      throw new IllegalStateException("OpenFGA tuple write failed", failure);
    }
  }

  @Override
  public void delete(String user, String relation, String object) {
    try {
      client.deleteTuples(List.of(new ClientTupleKeyWithoutCondition()
          .user(user).relation(relation)._object(object))).get();
    } catch (Exception failure) {
      // OpenFGA deletion is idempotent from the outbox consumer perspective.
      if (!String.valueOf(failure.getMessage()).contains("tuple_not_found")) {
        throw new IllegalStateException("OpenFGA tuple delete failed", failure);
      }
    }
  }
}
