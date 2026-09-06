package com.aurevia.authz.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aurevia.authz.openfga.RelationshipAuthorizationPort;
import dev.openfga.sdk.api.client.OpenFgaClient;
import dev.openfga.sdk.api.client.model.ClientReadResponse;
import dev.openfga.sdk.api.configuration.ClientReadOptions;
import dev.openfga.sdk.api.model.ConsistencyPreference;
import dev.openfga.sdk.api.model.Tuple;
import dev.openfga.sdk.api.model.TupleKey;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class OpenFgaReconciliationServiceTest {
  @Test void readsEveryPageWithHigherConsistencyBeforeCalculatingDrift() throws Exception {
    OpenFgaReconciliationRepository repository=mock(OpenFgaReconciliationRepository.class);
    RelationshipAuthorizationPort relationships=mock(RelationshipAuthorizationPort.class);
    OpenFgaClient client=mock(OpenFgaClient.class);
    when(repository.expectedTuples()).thenReturn(Set.of(
        new ReconciliationTuple("user:one","viewer","resource:page/one"),
        new ReconciliationTuple("user:two","viewer","resource:page/two")));
    ClientReadResponse first=response("next",tuple("user:one","viewer","resource:page/one"));
    ClientReadResponse second=response("",tuple("user:two","viewer","resource:page/two"));
    when(client.read(any(),any(ClientReadOptions.class))).thenReturn(
        CompletableFuture.completedFuture(first),CompletableFuture.completedFuture(second));
    var service=new OpenFgaReconciliationService(repository,relationships,client);

    var report=service.reconcile(false);

    assertThat(report.missing()).isEmpty();
    assertThat(report.unexpected()).isEmpty();
    ArgumentCaptor<ClientReadOptions> options=ArgumentCaptor.forClass(ClientReadOptions.class);
    org.mockito.Mockito.verify(client,org.mockito.Mockito.times(2)).read(any(),options.capture());
    assertThat(options.getAllValues()).allSatisfy(value->{
      assertThat(value.getPageSize()).isEqualTo(100);
      assertThat(value.getConsistency()).isEqualTo(ConsistencyPreference.HIGHER_CONSISTENCY);
    });
    assertThat(options.getAllValues().get(1).getContinuationToken()).isEqualTo("next");
  }

  private static ClientReadResponse response(String continuation,Tuple... tuples) {
    ClientReadResponse response=mock(ClientReadResponse.class);
    when(response.getTuples()).thenReturn(List.of(tuples));
    when(response.getContinuationToken()).thenReturn(continuation);
    return response;
  }

  private static Tuple tuple(String user,String relation,String object) {
    return new Tuple().key(new TupleKey().user(user).relation(relation)._object(object));
  }
}
