package com.aurevia.authz.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aurevia.authz.access.AccessAdministrationService;
import com.aurevia.authz.access.AccessModels.GrantCommand;
import com.aurevia.authz.api.dto.AuthorizationDtos.CheckRequest;
import com.aurevia.authz.api.dto.IdentitySyncDtos.LoginIdentityRequest;
import com.aurevia.authz.api.dto.ResourceManifestDtos.*;
import com.aurevia.authz.api.dto.UiPluginDtos.ArtifactRequest;
import com.aurevia.authz.authorization.AuthorizationDecisionService;
import com.aurevia.authz.identity.IdentitySyncService;
import com.aurevia.authz.openfga.RelationshipAuthorizationPort;
import com.aurevia.authz.support.PermissionStack;
import com.aurevia.authz.sync.OutboxReconciler;
import com.aurevia.authz.ui.UiPluginRegistryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@EnabledIfEnvironmentVariable(named="AUREVIA_TEST_OPENFGA_URL",matches=".+")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
class ManifestReleaseIntegrationTest {
  private static final String ISSUER="https://manifest.test/realm";
  private static final PermissionStack STACK;
  static {
    try { STACK=PermissionStack.configured()?PermissionStack.provision():null; }
    catch(Exception failure) { throw new IllegalStateException(failure); }
  }
  @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
    if(STACK==null)return;
    r.add("spring.datasource.url",()->STACK.jdbcUrl);
    r.add("spring.datasource.username",()->STACK.jdbcUser);
    r.add("spring.datasource.password",()->STACK.jdbcPassword);
    r.add("spring.data.redis.host",()->STACK.redisHost);
    r.add("spring.data.redis.port",()->STACK.redisPort);
    r.add("spring.data.redis.password",()->STACK.redisPassword);
    r.add("aurevia.openfga.base-url",()->STACK.openFgaUrl);
    r.add("aurevia.openfga.store-id",()->STACK.storeId);
    r.add("aurevia.openfga.model-id",()->STACK.modelId);
    r.add("aurevia.openfga.reconcile-on-startup",()->"false");
    r.add("aurevia.outbox.interval-ms",()->"3600000");
    r.add("aurevia.ui-artifacts.network-policy",()->"DEVELOPMENT");
    r.add("aurevia.ui-artifacts.development-host",()->"localhost");
    r.add("aurevia.ui-artifacts.allow-http",()->"true");
    r.add("aurevia.ui-artifacts.require-integrity",()->"false");
    r.add("aurevia.internal.username",()->"bff");
    r.add("aurevia.internal.password",()->"manifest-test");
  }
  @AfterAll static void cleanup() throws Exception { if(STACK!=null)STACK.dropDatabase(); }
  @Autowired ResourceManifestService manifests;
  @Autowired UiPluginRegistryService artifacts;
  @Autowired PanelAdministrationService panels;
  @Autowired IdentitySyncService identities;
  @Autowired AccessAdministrationService access;
  @Autowired AuthorizationDecisionService authorization;
  @Autowired RelationshipAuthorizationPort relationships;
  @Autowired OutboxReconciler outbox;
  @Autowired JdbcClient db;
  @Autowired ObjectMapper json;
  @Autowired TestRestTemplate http;
  private UUID panel,user;
  private String slug,subject,canonical;

  @BeforeEach void setup() {
    slug="release-"+UUID.randomUUID().toString().substring(0,8);
    panel=panels.create(new PanelModels.PanelCommand(slug.toUpperCase(),slug,slug,null,slug,
        slug,slug.replace('-','_'),"page-a","http://localhost:3001/remoteEntry.js","./plugin",
        "/"+slug,"1.0.0","1.0",null,"HYBRID","REAL",null,null,true,1),"test").id();
    db.sql("""
        insert into identity_provider(code,name,provider_type,issuer_url,authorization_endpoint,
          token_endpoint,jwks_uri,client_id,client_secret_reference,subject_claim,
          connection_status,created_by,updated_by)
        values('test','Manifest IdP','OIDC',:issuer,:issuer||'/auth',:issuer||'/token',
          :issuer||'/jwks','manifest','secret://manifest','employee_id','ACTIVE','test','test')
        on conflict(code) do nothing
        """).param("issuer",ISSUER).update();
    subject=UUID.randomUUID().toString();
    user=identities.sync(new LoginIdentityRequest("test",ISSUER,subject,slug,slug,null,List.of(),
        null,null,null,Map.of())).userId();
    canonical=db.sql("select canonical_user_id from app_user where id=:id").param("id",user)
        .query(String.class).single();
    grant("application:aurevia","admin");
    drain();
  }

  @Test void publishRollbackAndRollForwardCoordinateCatalogActionsParentsBindingsAndAuthorization() throws Exception {
    UUID v1=stage("1.0.0",false),a1=artifact("1.0.0",v1,false);
    manifests.publish(panel,v1,"test");
    drain();
    assertThat(status("page:"+slug+".a")).isEqualTo("ACTIVE");
    assertThat(decision("page:"+slug+".a","view")).isEqualTo("ALLOW");
    assertPair(v1,a1);
    String history=history(v1);

    UUID v2=stage("2.0.0",true),a2=artifact("2.0.0",v2,true);
    manifests.publish(panel,v2,"test");
    // New catalog is committed, but old inherited/cache decisions are not usable during projection.
    assertThat(decision("page:"+slug+".a","view")).isEqualTo("DENY");
    drain();
    assertThat(status("page:"+slug+".b")).isEqualTo("ACTIVE");
    assertThat(decision("page:"+slug+".b","view")).isEqualTo("ALLOW");
    grant("page:"+slug+".b","view");
    grant("page:"+slug+".a","update");
    drain();
    assertThat(relationships.check("user:"+canonical,"viewer",object("page:"+slug+".b"))).isTrue();
    assertPair(v2,a2);

    // A manual definition is not removed by manifest materialization.
    db.sql("""
        insert into resource(resource_key,type,parent_id,name_fa,name_en,source,panel_id)
        values(:key,'PAGE',:parent,'Manual','Manual','ADMIN',:panel)
        """).param("key","page:"+slug+".manual").param("parent",resource("module:"+slug))
        .param("panel",panel).update();

    var response=http.exchange("/internal/v1/registry/panels/"+panel+"/artifacts/"+a1+
        "/activate?version="+version(),HttpMethod.POST,new HttpEntity<>(headers()),Map.class);
    assertThat(response.getStatusCode().is2xxSuccessful()).as("%s",response.getBody()).isTrue();
    assertPair(v1,a1);
    assertThat(status("page:"+slug+".b")).isEqualTo("DEPRECATED");
    assertThat(status("page:"+slug+".manual")).isEqualTo("ACTIVE");
    assertThat(actions("page:"+slug+".a")).containsExactly("view");
    assertThat(parent("page:"+slug+".a")).isEqualTo(resource("module:"+slug));
    assertThat(decision("page:"+slug+".b","view")).isEqualTo("DENY");
    assertThat(decision("page:"+slug+".a","update")).isEqualTo("DENY");
    drain();
    assertThat(relationships.check("user:"+canonical,"can_view",object("page:"+slug+".b"))).isFalse();
    assertThat(relationships.check("user:"+canonical,"editor",object("page:"+slug+".a"))).isFalse();
    assertParentTuple("page:"+slug+".a","module:"+slug,true);
    assertParentTuple("page:"+slug+".a","module:"+slug+".other",false);
    assertParentTuple("page:"+slug+".b","module:"+slug,false);
    var effective=authorization.manifest(subject,ISSUER);
    assertThat(effective.permissions()).doesNotContainKey("page:"+slug+".b");
    assertThat(effective.permissions().get("page:"+slug+".a")).containsExactly("view");
    assertThat(effective.uiCatalog().modules()).filteredOn(m->m.moduleKey().equals(slug))
        .singleElement().satisfies(m->{
          assertThat(m.remote().artifactVersion()).isEqualTo("1.0.0");
          assertThat(m.routes()).extracting(r->r.id()).containsExactly("page-a");
        });
    assertThat(history(v1)).isEqualTo(history);
    assertThat(manifests.definition(slug).manifestVersion()).isEqualTo("1.0.0");
    assertThat(binding()).containsExactly("old");
    assertThat(manifests.drafts(panel)).filteredOn(ManifestDraftView::active)
        .extracting(ManifestDraftView::id).containsExactly(v1);

    var forward=http.exchange("/internal/v1/registry/panels/"+panel+"/resource-manifests/"+v2+
        "/activate",HttpMethod.POST,new HttpEntity<>(headers()),Map.class);
    assertThat(forward.getStatusCode().is2xxSuccessful()).as("%s",forward.getBody()).isTrue();
    drain();
    assertPair(v2,a2);
    assertThat(status("page:"+slug+".b")).isEqualTo("ACTIVE");
    assertThat(decision("page:"+slug+".b","view")).isEqualTo("ALLOW");
    assertThat(actions("page:"+slug+".a")).containsExactlyInAnyOrder("view","update");
    assertThat(binding()).containsExactly("new");
    assertParentTuple("page:"+slug+".a","module:"+slug+".other",true);
    assertThat(relationships.check("user:"+canonical,"viewer",object("page:"+slug+".b"))).isTrue();
  }

  @Test void failedMaterializationRollsBackCatalogPointersBindingsAndOutbox() {
    UUID v1=stage("1.0.0",false),a1=artifact("1.0.0",v1,false);
    manifests.publish(panel,v1,"test");drain();
    UUID v2=stage("2.0.0",true);
    artifact("2.0.0",v2,true);
    // Force failure after earlier resources/actions have already been materialized.
    String constraint="fail_manifest_"+slug.replace('-','_');
    db.sql("alter table resource add constraint "+constraint+
        " check (resource_key<>'page:"+slug+".b')").update();
    long outboxBefore=db.sql("select count(*) from outbox_event").query(Long.class).single();
    long versionBefore=version();
    try {
      assertThatThrownBy(()->manifests.publish(panel,v2,"test")).isInstanceOf(RuntimeException.class);
      assertPair(v1,a1);
      assertThat(version()).isEqualTo(versionBefore);
      assertThat(actions("page:"+slug+".a")).containsExactly("view");
      assertThat(parent("page:"+slug+".a")).isEqualTo(resource("module:"+slug));
      assertThat(db.sql("select count(*) from outbox_event").query(Long.class).single()).isEqualTo(outboxBefore);
      assertThat(manifests.preview(panel,v2).workflowStatus()).isEqualTo("DRAFT");
      assertThat(binding()).containsExactly("old");
    } finally { db.sql("alter table resource drop constraint "+constraint).update(); }
  }

  @Test void mismatchedArtifactAndUnpublishedActivationAreRejectedWithoutChangingActiveRelease() {
    UUID v1=stage("1.0.0",false),a1=artifact("1.0.0",v1,false);
    manifests.publish(panel,v1,"test");drain();
    UUID v2=stage("2.0.0",true),a2=artifact("2.0.0",v2,true);
    assertThatThrownBy(()->artifacts.activate(panel,a2,version())).hasMessageContaining("PUBLISHED");
    assertThatThrownBy(()->manifests.activate(panel,v1,"test",a2)).hasMessageContaining("another resource revision");
    assertThatThrownBy(()->artifacts.activate(panel,a1,version(),v2)).hasMessageContaining("immutable");
    assertPair(v1,a1);
  }

  private UUID stage(String version,boolean next) {
    List<ManifestResource> values=new ArrayList<>();
    values.add(node("module:"+slug+".other","MODULE",null,List.of("view"),null));
    values.add(node("page:"+slug+".a","PAGE",next?"module:"+slug+".other":null,
        next?List.of("view","update"):List.of("view"),null));
    if(next)values.add(node("page:"+slug+".b","PAGE",null,List.of("view"),null));
    values.add(node("external_resource:"+slug+".asset","EXTERNAL_RESOURCE",null,List.of("view"),next?"new":"old"));
    return manifests.stage(panel,new ResourceManifest("1.0",new ModuleMetadata(slug,slug,null,slug,version),values),"test").id();
  }
  private ManifestResource node(String key,String type,String parent,List<String> actions,String external) {
    return new ManifestResource(key,type,parent,null,key,key,key,slug,"INTERNAL",actions,Map.of(),
        external==null?null:slug,external==null?null:"dashboard",external);
  }
  private UUID artifact(String version,UUID revision,boolean next) {
    String routes="{\"key\":\"page-a\",\"path\":\"\",\"requiredResource\":\"page:"+slug+".a\",\"requiredAction\":\"view\"}";
    if(next)routes+=",{\"key\":\"page-b\",\"path\":\"b\",\"requiredResource\":\"page:"+slug+".b\",\"requiredAction\":\"view\"}";
    String manifest="{\"schemaVersion\":\"1.0\",\"microfrontend\":{\"key\":\""+slug+"\",\"version\":\""+version+"\"},\"routes\":["+routes+"],\"navigation\":[]}";
    return artifacts.publish(panel,"test",new ArtifactRequest(version,"http://localhost:3001/remoteEntry.js",
        slug.replace('-','_'),"./plugin","1.0",null,manifest,revision)).id();
  }
  private void grant(String key,String action) {
    UUID actionId=db.sql("select id from action where action_key=:key").param("key",action).query(UUID.class).single();
    access.grant(new GrantCommand(null,"USER",user,resource(key),actionId,null),"test");
  }
  private UUID resource(String key) { return db.sql("select id from resource where resource_key=:key").param("key",key).query(UUID.class).single(); }
  private UUID parent(String key) { return db.sql("select parent_id from resource where resource_key=:key").param("key",key).query(UUID.class).single(); }
  private String status(String key) { return db.sql("select status::text from resource where resource_key=:key").param("key",key).query(String.class).single(); }
  private List<String> actions(String key) {
    return db.sql("select a.action_key from resource_action ra join action a on a.id=ra.action_id where ra.resource_id=:id order by a.action_key")
        .param("id",resource(key)).query(String.class).list();
  }
  private List<String> binding() { return db.sql("select external_id from resource_external_binding where resource_id=:id and active")
      .param("id",resource("external_resource:"+slug+".asset")).query(String.class).list(); }
  private String history(UUID id) { return db.sql("select row_to_json(m)::text from resource_manifest_import m where id=:id").param("id",id).query(String.class).single(); }
  private long version() { return db.sql("select version from panel where id=:id").param("id",panel).query(Long.class).single(); }
  private String object(String key) { return key.startsWith("application:")?key:"resource:"+key.replace(':','/'); }
  private String decision(String key,String action) { return authorization.check(new CheckRequest(subject,ISSUER,object(key),action,Map.of(),"release-test")).decision().result(); }
  private HttpHeaders headers() { var h=new HttpHeaders();h.setBasicAuth("bff","manifest-test");h.set("X-Actor","test");h.set("X-Actor-Issuer",ISSUER);h.set("X-Actor-Subject",subject);return h; }
  private void assertPair(UUID revision,UUID artifact) {
    var pair=db.sql("select active_resource_manifest_id,active_artifact_id from panel where id=:id").param("id",panel).query().singleRow();
    assertThat(pair.get("active_resource_manifest_id")).isEqualTo(revision);
    assertThat(pair.get("active_artifact_id")).isEqualTo(artifact);
  }
  private void assertParentTuple(String child,String parent,boolean present) throws Exception {
    var result=STACK.directClient().read(new dev.openfga.sdk.api.client.model.ClientReadRequest()
        .user(object(parent)).relation("parent")._object(object(child))).get();
    assertThat(!result.getTuples().isEmpty()).as("%s parent %s",child,parent).isEqualTo(present);
  }
  private void drain() {
    long until=System.nanoTime()+Duration.ofSeconds(40).toNanos();
    while(System.nanoTime()<until) {
      outbox.reconcileBatch();
      long pending=db.sql("select count(*) from outbox_event where processed_at is null").query(Long.class).single();
      if(pending==0)return;
      try { Thread.sleep(25); } catch(InterruptedException failure) { throw new IllegalStateException(failure); }
    }
    throw new AssertionError(db.sql("select event_type,last_error from outbox_event where processed_at is null").query().listOfRows());
  }
}
