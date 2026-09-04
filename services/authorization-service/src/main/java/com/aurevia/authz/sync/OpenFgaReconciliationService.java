package com.aurevia.authz.sync;

import com.aurevia.authz.openfga.RelationshipAuthorizationPort;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class OpenFgaReconciliationService {
  private final OpenFgaReconciliationRepository reconciliation;
  private final RelationshipAuthorizationPort relationships;
  private final RestClient openfga;
  private final String storeId;

  public OpenFgaReconciliationService(OpenFgaReconciliationRepository reconciliation,
      RelationshipAuthorizationPort relationships, RestClient.Builder rest,
      @Value("${aurevia.openfga.base-url}") String baseUrl,
      @Value("${aurevia.openfga.store-id}") String storeId) {
    this.reconciliation=reconciliation;this.relationships=relationships;
    this.openfga=rest.baseUrl(baseUrl).build();this.storeId=storeId;
  }

  public Report reconcile(boolean repair) {
    Set<ReconciliationTuple> expected=reconciliation.expectedTuples();
    Set<ReconciliationTuple> actual=actual();
    Set<ReconciliationTuple> missing=new LinkedHashSet<>(expected);missing.removeAll(actual);
    Set<ReconciliationTuple> unexpected=new LinkedHashSet<>(actual);unexpected.removeAll(expected);
    if(repair){missing.forEach(t->relationships.write(t.user(),t.relation(),t.object()));unexpected.forEach(t->relationships.delete(t.user(),t.relation(),t.object()));}
    return new Report(!repair,expected.size(),actual.size(),List.copyOf(missing),
        List.copyOf(unexpected),repair ? missing.size()+unexpected.size() : 0);
  }

  private Set<ReconciliationTuple> actual(){
    Set<ReconciliationTuple> result=new LinkedHashSet<>();String token="";
    do {
      final String continuation=token;
      Map<String,Object> request=continuation.isBlank()?Map.of("page_size",100):Map.of("page_size",100,"continuation_token",continuation);
      JsonNode response=openfga.post().uri("/stores/{store}/read",storeId)
          .body(request).retrieve().body(JsonNode.class);
      if(response==null)throw new IllegalStateException("Empty OpenFGA tuple response");
      response.path("tuples").forEach(node->{JsonNode key=node.path("key");result.add(new ReconciliationTuple(
          key.path("user").asText(),key.path("relation").asText(),key.path("object").asText()));});
      token=response.path("continuation_token").asText("");
    } while(!token.isBlank());
    return result;
  }

  public record Report(boolean dryRun,int expectedCount,int actualCount,
      List<ReconciliationTuple> missing,List<ReconciliationTuple> unexpected,int repairedCount){}
}
