package com.aurevia.authz.sync;

import com.aurevia.authz.openfga.RelationshipAuthorizationPort;
import dev.openfga.sdk.api.client.OpenFgaClient;
import dev.openfga.sdk.api.client.model.ClientReadRequest;
import dev.openfga.sdk.api.configuration.ClientReadOptions;
import dev.openfga.sdk.api.model.ConsistencyPreference;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class OpenFgaReconciliationService {
  private final OpenFgaReconciliationRepository reconciliation;
  private final RelationshipAuthorizationPort relationships;
  private final OpenFgaClient openfga;

  public OpenFgaReconciliationService(OpenFgaReconciliationRepository reconciliation,
      RelationshipAuthorizationPort relationships,OpenFgaClient openfga) {
    this.reconciliation=reconciliation;this.relationships=relationships;
    this.openfga=openfga;
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
    Set<ReconciliationTuple> result=new LinkedHashSet<>();String token=null;
    try {
      do {
        var options=new ClientReadOptions().pageSize(100)
            .consistency(ConsistencyPreference.HIGHER_CONSISTENCY);
        if(token!=null&&!token.isBlank())options.continuationToken(token);
        var response=openfga.read(new ClientReadRequest(),options).get();
        if(response==null)throw new IllegalStateException("Empty OpenFGA tuple response");
        if(response.getTuples()!=null)response.getTuples().forEach(tuple->{
          var key=tuple.getKey();
          if(key==null)throw new IllegalStateException("OpenFGA tuple key is missing");
          result.add(new ReconciliationTuple(key.getUser(),key.getRelation(),key.getObject()));
        });
        token=response.getContinuationToken();
      } while(token!=null&&!token.isBlank());
      return result;
    } catch(InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("OpenFGA reconciliation read was interrupted",interrupted);
    } catch(IllegalStateException failure) {
      throw failure;
    } catch(Exception failure) {
      throw new IllegalStateException("OpenFGA reconciliation read failed",failure);
    }
  }

  public record Report(boolean dryRun,int expectedCount,int actualCount,
      List<ReconciliationTuple> missing,List<ReconciliationTuple> unexpected,int repairedCount){}
}
