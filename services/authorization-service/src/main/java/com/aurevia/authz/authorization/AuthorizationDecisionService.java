package com.aurevia.authz.authorization;

import static com.aurevia.authz.api.dto.AuthorizationDtos.*;

import com.aurevia.authz.audit.AuthorizationDecisionAuditor;
import com.aurevia.authz.identity.SubjectKey;
import com.aurevia.authz.openfga.RelationshipAuthorizationPort;
import com.aurevia.authz.openfga.RelationshipAuthorizationPort.RelationshipCheck;
import com.aurevia.authz.policy.RuntimePolicyService;
import com.aurevia.authz.semantics.AuthorizationSemanticsRegistry;
import com.aurevia.authz.semantics.ResourceObjectKey;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class AuthorizationDecisionService {
  private static final TypeReference<Map<String,Object>> OBJECT_MAP=new TypeReference<>() {};
  private final RelationshipAuthorizationPort relationships;
  private final AuthorizationQueryRepository queries;
  private final AuthorizationSemanticsRegistry semantics;
  private final RuntimePolicyService policies;
  private final AuthorizationDecisionAuditor auditor;
  private final ObjectMapper json;

  public AuthorizationDecisionService(RelationshipAuthorizationPort relationships,
      AuthorizationQueryRepository queries,AuthorizationSemanticsRegistry semantics,
      RuntimePolicyService policies,AuthorizationDecisionAuditor auditor,ObjectMapper json) {
    this.relationships=relationships;this.queries=queries;this.semantics=semantics;
    this.policies=policies;this.auditor=auditor;this.json=json;
  }

  public CheckEvaluation check(CheckRequest request) {
    long started=System.nanoTime();
    String decisionId=UUID.randomUUID().toString();
    String permission=semantics.resolveObject(request.resource(),request.action()).permission();
    SubjectKey subject=new SubjectKey(request.issuer(),request.subjectId());
    long openFgaStarted=System.nanoTime();
    boolean relationshipAllowed=relationships.check(subject.openFgaUser(),permission,
        request.resource());
    long openFgaDuration=(System.nanoTime()-openFgaStarted)/1_000_000;
    RuntimePolicyService.Evaluation policy=relationshipAllowed
        ?policies.evaluate(request.issuer(),request.subjectId(),request.resource(),request.action())
        :new RuntimePolicyService.Evaluation(false,"NOT_EVALUATED",Map.of(),List.of());
    boolean allowed=relationshipAllowed&&policy.allowed();
    String reason=!relationshipAllowed?"NO_RELATIONSHIP":policy.reasonCode();
    long latency=(System.nanoTime()-started)/1_000_000;
    auditor.record(new AuthorizationDecisionAuditor.Record(decisionId,request.subjectId(),
        request.resource(),request.action(),permission,relationshipAllowed,policy.allowed(),
        allowed,reason,latency,request.correlationId(),policy.policies()));
    return new CheckEvaluation(new Decision(allowed?"ALLOW":"DENY",reason,
        "configured-model",decisionId,allowed?policy.obligations():Map.of()),
        permission,openFgaDuration);
  }

  public Manifest manifest(String subjectId,String issuer) {
    SubjectKey subject=new SubjectKey(issuer,subjectId);
    List<AuthorizationQueryRepository.PanelRecord> panels=queries.activePanels().stream()
        .filter(panel->relationships.check(subject.openFgaUser(),"can_view",
            "application:aurevia/"+panel.slug())).toList();
    List<AuthorizationQueryRepository.PermissionCandidate> candidates=
        queries.permissionCandidates();
    List<RelationshipCheck> checks=candidates.stream().map(candidate->new RelationshipCheck(
        subject.openFgaUser(),semantics.resolve(candidate.resourceType(),candidate.actionKey())
            .permission(),ResourceObjectKey.from(candidate.resourceType(),candidate.resourceKey())))
        .toList();
    Map<RelationshipCheck,Boolean> decisions=relationships.checkBatch(checks);
    Map<String,List<String>> permissions=new LinkedHashMap<>();
    for(int index=0;index<candidates.size();index++) {
      if(!Boolean.TRUE.equals(decisions.get(checks.get(index)))) continue;
      var candidate=candidates.get(index);
      permissions.computeIfAbsent(candidate.resourceKey(),ignored->new ArrayList<>())
          .add(candidate.actionKey());
    }
    List<AuthorizationQueryRepository.ResourceRecord> catalog=queries.activeResources();
    Map<UUID,AuthorizationQueryRepository.ResourceRecord> byId=new HashMap<>();
    catalog.forEach(resource->byId.put(resource.id(),resource));
    Set<UUID> included=new LinkedHashSet<>();
    catalog.stream().filter(resource->permissions.containsKey(resource.resourceKey()))
        .forEach(resource->{
          UUID cursor=resource.id();
          while(cursor!=null&&included.add(cursor)) {
            var node=byId.get(cursor);
            cursor=node==null?null:node.parentId();
          }
        });
    List<ResourceNode> tree=catalog.stream().filter(resource->included.contains(resource.id()))
        .map(resource->new ResourceNode(resource.id(),resource.parentId(),resource.resourceKey(),
            resource.type(),resource.nameFa(),resource.nameEn(),resource.ownerDomain(),
            resource.classification(),permissions.getOrDefault(resource.resourceKey(),List.of())))
        .toList();
    List<Map<String,Object>> modules=panels.stream()
        .map(panel->uiModule(panel,permissions)).filter(Objects::nonNull).toList();
    List<PanelSummary> publicPanels=panels.stream().map(AuthorizationDecisionService::panelSummary)
        .toList();
    String version=manifestVersion(publicPanels,permissions,tree,modules);
    Instant generated=Instant.now();
    return new Manifest("EFFECTIVE_USER_MANIFEST",
        new SubjectView("user",issuer,subjectId),version,generated.plusSeconds(60),
        publicPanels,permissions,tree,new UiCatalog(version,generated,"1.0",modules));
  }

  private Map<String,Object> uiModule(AuthorizationQueryRepository.PanelRecord panel,
      Map<String,List<String>> permissions) {
    try {
      JsonNode manifest=json.readTree(panel.manifestJson());
      List<Map<String,Object>> routes=new ArrayList<>();
      Set<String> routeIds=new HashSet<>();
      for(JsonNode route:manifest.path("routes")) {
        String resource=route.path("resource").asText(null);
        String action=route.path("action").asText("view");
        if(resource!=null&&!permissions.getOrDefault(resource,List.of()).contains(action)) continue;
        routes.add(json.convertValue(route,OBJECT_MAP));
        routeIds.add(route.path("id").asText());
      }
      Map<String,AuthorizationQueryRepository.MenuOverride> overrides=
          queries.menuOverrides(panel.id()).stream().collect(Collectors.toMap(
              AuthorizationQueryRepository.MenuOverride::menuId,value->value));
      List<Map<String,Object>> menus=new ArrayList<>();
      for(JsonNode item:manifest.path("menus")) {
        if(!routeIds.contains(item.path("routeId").asText())) continue;
        var override=overrides.get(item.path("id").asText());
        if(override!=null&&override.hidden()) continue;
        Map<String,Object> menu=json.convertValue(item,OBJECT_MAP);
        if(override!=null) {
          if(override.title()!=null) menu.put("title",override.title());
          if(override.icon()!=null) menu.put("icon",override.icon());
          if(override.sortOrder()!=null) menu.put("order",override.sortOrder());
        }
        menus.add(menu);
      }
      menus.sort(Comparator.comparingInt(value->((Number)value.getOrDefault("order",0)).intValue()));
      Map<String,Object> remote=new LinkedHashMap<>();
      remote.put("remoteEntryUrl",panel.remoteEntryUrl());
      remote.put("remoteName",panel.artifactRemoteName());
      remote.put("exposedModule",panel.artifactExposedModule());
      remote.put("contractVersion",panel.artifactContractVersion());
      remote.put("artifactVersion",panel.artifactVersion());
      if(panel.artifactIntegrity()!=null) remote.put("integrity",panel.artifactIntegrity());
      Map<String,Object> module=new LinkedHashMap<>();
      module.put("registrationId",panel.id());module.put("moduleKey",panel.slug());
      module.put("displayName",panel.nameFa());module.put("displayNameEn",panel.nameEn());
      module.put("description",panel.description());module.put("order",panel.sortOrder());
      module.put("routePrefix",panel.routeBasePath().replaceFirst("^/",""));
      module.put("defaultRouteId",panel.defaultRouteId());module.put("remote",remote);
      module.put("runtime",Map.of("apiBasePath","/api/proxy/"+panel.serviceSlug()));
      module.put("routes",routes);module.put("menus",menus);
      return module;
    } catch(Exception failure) {
      throw new IllegalStateException("stored UI manifest is invalid",failure);
    }
  }

  private static PanelSummary panelSummary(AuthorizationQueryRepository.PanelRecord panel) {
    return new PanelSummary(panel.id(),panel.code(),panel.slug(),panel.nameFa(),panel.nameEn(),
        panel.remoteEntryUrl(),panel.artifactExposedModule(),panel.routeBasePath(),
        panel.artifactVersion(),panel.artifactContractVersion(),panel.artifactIntegrity());
  }

  private String manifestVersion(Object panels,Object permissions,Object resources,Object modules) {
    try {
      Map<String,Object> canonical=new LinkedHashMap<>();
      canonical.put("panels",panels);canonical.put("permissions",permissions);
      canonical.put("resources",resources);canonical.put("modules",modules);
      return "manifest-sha256-"+HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
          .digest(json.writeValueAsBytes(canonical)));
    } catch(Exception failure) {
      throw new IllegalStateException("Unable to version manifest",failure);
    }
  }

  public record CheckEvaluation(Decision decision,String permission,long openFgaDurationMs) {}
}
