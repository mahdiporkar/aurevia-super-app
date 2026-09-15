package com.aurevia.authz.access;

import static com.aurevia.authz.access.AccessModels.*;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aurevia.authz.observability.AuditTrail;
import com.aurevia.authz.semantics.AuthorizationSemanticsRegistry;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;

class AccessAdministrationServiceTest {
  private final AccessRepository repository=mock(AccessRepository.class);
  private final AccessAdministrationService service=new AccessAdministrationService(repository,
      new AuthorizationSemanticsRegistry(),mock(AuditTrail.class),
      new ResourceTreeDevelopmentPolicy(false));

  @Test void manifestModeRejectsManualResourceCreation() {
    UUID panel=UUID.randomUUID();UUID parent=UUID.randomUUID();
    when(repository.resourceExists(parent)).thenReturn(true);
    when(repository.parentResource(parent)).thenReturn(Optional.of(new ParentResource("MODULE",panel)));
    when(repository.panelResourceMode(panel)).thenReturn(Optional.of("MANIFEST"));

    assertThatThrownBy(()->service.createResource(command(panel,parent),"operator"))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("MANIFEST mode");
  }

  @Test void hybridModeAllowsAdministratorOwnedResource() {
    UUID panel=UUID.randomUUID();UUID parent=UUID.randomUUID();
    when(repository.resourceExists(parent)).thenReturn(true);
    when(repository.parentResource(parent)).thenReturn(Optional.of(new ParentResource("MODULE",panel)));
    when(repository.panelResourceMode(panel)).thenReturn(Optional.of("HYBRID"));

    service.createResource(command(panel,parent),"operator");

    verify(repository).createResource(org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.eq("ADMIN"));
  }

  @Test void manifestResourceAllowsVisibilityButRejectsMetadataMutation() {
    UUID panel=UUID.randomUUID();UUID parent=UUID.randomUUID();UUID resource=UUID.randomUUID();
    var snapshot=snapshot(panel,parent,"MANIFEST",Map.of("owner","manifest"));
    when(repository.resource(resource)).thenReturn(Optional.of(snapshot));
    when(repository.resourceExists(parent)).thenReturn(true);
    when(repository.parentResource(parent)).thenReturn(Optional.of(new ParentResource("MODULE",panel)));

    ResourceCommand changed=new ResourceCommand(snapshot.resourceKey(),snapshot.type(),parent,
        "نام تغییرکرده",snapshot.nameEn(),snapshot.ownerDomain(),snapshot.classification(),
        null,null,null,"MANIFEST",panel,false,null);

    assertThatThrownBy(()->service.updateResource(resource,0,changed,"operator"))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("manifest-owned metadata");
    verify(repository,never()).updateResource(any(),eq(0L),any(),any());
  }

  @Test void manifestSupportedActionsCannotBeChangedFromAdministrationApi() {
    UUID resource=UUID.randomUUID();
    when(repository.resource(resource)).thenReturn(Optional.of(
        snapshot(UUID.randomUUID(),UUID.randomUUID(),"MANIFEST",Map.of())));

    assertThatThrownBy(()->service.attachAction(resource,UUID.randomUUID(),"operator"))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("manifest-owned");
    verify(repository,never()).attachAction(any(),any());
  }

  @Test void omittedMetadataIsPreservedWhenAdministratorResourceIsUpdated() {
    UUID panel=UUID.randomUUID();UUID parent=UUID.randomUUID();UUID resource=UUID.randomUUID();
    Map<String,Object> metadata=Map.of("route","/manual");
    var snapshot=snapshot(panel,parent,"ADMIN",metadata);
    when(repository.resource(resource)).thenReturn(Optional.of(snapshot));
    when(repository.resourceExists(parent)).thenReturn(true);
    when(repository.parentResource(parent)).thenReturn(Optional.of(new ParentResource("MODULE",panel)));
    when(repository.updateResource(eq(resource),eq(3L),any(),eq("ADMIN"))).thenReturn(1);
    ResourceCommand request=new ResourceCommand(snapshot.resourceKey(),snapshot.type(),parent,
        snapshot.nameFa(),snapshot.nameEn(),snapshot.ownerDomain(),snapshot.classification(),
        null,null,null,"ADMIN",panel,true,null);

    service.updateResource(resource,3,request,"operator");

    ArgumentCaptor<ResourceCommand> saved=ArgumentCaptor.forClass(ResourceCommand.class);
    verify(repository).updateResource(eq(resource),eq(3L),saved.capture(),eq("ADMIN"));
    assertThat(saved.getValue().metadata()).isEqualTo(metadata);
  }

  @Test void developmentFlagAllowsAddingToManifestModeTree() {
    AccessAdministrationService developmentService=new AccessAdministrationService(repository,
        new AuthorizationSemanticsRegistry(),mock(AuditTrail.class),
        new ResourceTreeDevelopmentPolicy(true));
    UUID panel=UUID.randomUUID();UUID parent=UUID.randomUUID();
    when(repository.resourceExists(parent)).thenReturn(true);
    when(repository.parentResource(parent)).thenReturn(Optional.of(new ParentResource("MODULE",panel)));
    when(repository.panelResourceMode(panel)).thenReturn(Optional.of("MANIFEST"));

    developmentService.createResource(command(panel,parent),"operator");

    verify(repository).createResource(any(),any(),eq("ADMIN"));
  }

  @Test void developmentFlagAllowsManifestTreeMove() {
    AccessAdministrationService developmentService=new AccessAdministrationService(repository,
        new AuthorizationSemanticsRegistry(),mock(AuditTrail.class),
        new ResourceTreeDevelopmentPolicy(true));
    UUID panel=UUID.randomUUID();UUID oldParent=UUID.randomUUID();UUID newParent=UUID.randomUUID();
    UUID resource=UUID.randomUUID();
    var snapshot=snapshot(panel,oldParent,"MANIFEST",Map.of());
    when(repository.resource(resource)).thenReturn(Optional.of(snapshot));
    when(repository.resourceExists(newParent)).thenReturn(true);
    when(repository.parentResource(newParent)).thenReturn(Optional.of(new ParentResource("MODULE",panel)));
    when(repository.updateResource(eq(resource),eq(2L),any(),eq("MANIFEST"))).thenReturn(1);
    ResourceCommand moved=new ResourceCommand(snapshot.resourceKey(),snapshot.type(),newParent,
        snapshot.nameFa(),snapshot.nameEn(),snapshot.ownerDomain(),snapshot.classification(),
        null,null,null,"MANIFEST",panel,true,Map.of());

    developmentService.updateResource(resource,2,moved,"operator");

    verify(repository).enqueueParent(resource,oldParent,"RESOURCE_PARENT_DELETE");
    verify(repository).enqueueParent(resource,newParent,"RESOURCE_PARENT_WRITE");
  }

  @Test void developmentFlagAllowsManifestResourceDeprecation() {
    AccessAdministrationService developmentService=new AccessAdministrationService(repository,
        new AuthorizationSemanticsRegistry(),mock(AuditTrail.class),
        new ResourceTreeDevelopmentPolicy(true));
    UUID resource=UUID.randomUUID();
    when(repository.resource(resource)).thenReturn(Optional.of(
        snapshot(UUID.randomUUID(),UUID.randomUUID(),"MANIFEST",Map.of())));
    when(repository.deprecateResource(resource,4,true)).thenReturn(1);

    developmentService.deprecateResource(resource,4,"operator");

    verify(repository).deprecateResource(resource,4,true);
  }

  @Test void productionPolicyStillRejectsManifestResourceDeprecation() {
    UUID resource=UUID.randomUUID();
    when(repository.resource(resource)).thenReturn(Optional.of(
        snapshot(UUID.randomUUID(),UUID.randomUUID(),"MANIFEST",Map.of())));

    assertThatThrownBy(()->service.deprecateResource(resource,0,"operator"))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("manifest-owned");
    verify(repository,never()).deprecateResource(any(),eq(0L),eq(false));
  }

  @Test void resourceWithActiveChildrenCannotBeDeprecated() {
    UUID resource=UUID.randomUUID();
    when(repository.resource(resource)).thenReturn(Optional.of(
        snapshot(UUID.randomUUID(),UUID.randomUUID(),"ADMIN",Map.of())));
    when(repository.hasActiveChildren(resource)).thenReturn(true);

    assertThatThrownBy(()->service.deprecateResource(resource,0,"operator"))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("active children");
    verify(repository,never()).deprecateResource(any(),eq(0L),eq(false));
  }

  private static ResourceCommand command(UUID panel,UUID parent) {
    return new ResourceCommand("page:hr.manual","PAGE",parent,"صفحه دستی","Manual Page",
        "hr","INTERNAL",null,null,null,"ADMIN",panel,true,Map.of());
  }

  private static ResourceSnapshot snapshot(UUID panel,UUID parent,String source,
      Map<String,Object> metadata) {
    return new ResourceSnapshot("page:hr.manual","PAGE",parent,"صفحه دستی","Manual Page",
        "hr","INTERNAL",null,null,null,source,panel,true,metadata);
  }
}
