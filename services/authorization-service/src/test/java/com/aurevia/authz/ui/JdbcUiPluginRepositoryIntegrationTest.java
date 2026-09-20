package com.aurevia.authz.ui;

import static org.assertj.core.api.Assertions.assertThat;

import com.aurevia.authz.support.PostgresFixture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.util.UUID;

@EnabledIfEnvironmentVariable(named="AUREVIA_TEST_JDBC_URL",matches=".+")
class JdbcUiPluginRepositoryIntegrationTest {
  @Test void unpublishedPanelListsArtifactsAndFindsVersionBeforeAndAfterActivation() throws Exception {
    try(var fixture=PostgresFixture.migrated()) {
      var db=fixture.database();
      UUID panel=db.sql("select id from panel where slug='hr'").query(UUID.class).single();
      db.sql("update panel set active_artifact_id=null where id=:id").param("id",panel).update();
      var repository=new JdbcUiPluginRepository(db);
      var artifacts=repository.artifacts(panel);
      assertThat(artifacts).isNotEmpty().allMatch(artifact->!artifact.active());
      var candidate=artifacts.getFirst();
      assertThat(repository.artifactByVersion(panel,candidate.artifactVersion()).orElseThrow().active()).isFalse();
      assertThat(repository.activate(panel,candidate.id(),candidate.panelVersion())).isTrue();
      assertThat(repository.artifactByVersion(panel,candidate.artifactVersion()).orElseThrow().active()).isTrue();
      assertThat(repository.artifacts(panel).stream().filter(artifact->artifact.active()).toList())
          .singleElement().satisfies(artifact->assertThat(artifact.id()).isEqualTo(candidate.id()));
    }
  }
}
