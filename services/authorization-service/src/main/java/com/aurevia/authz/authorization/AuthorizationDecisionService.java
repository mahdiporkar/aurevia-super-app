package com.aurevia.authz.authorization;

import static com.aurevia.authz.api.dto.AuthorizationDtos.*;

import com.aurevia.authz.audit.AuthorizationDecisionAuditor;
import com.aurevia.authz.identity.SubjectKey;
import com.aurevia.authz.openfga.RelationshipAuthorizationPort;
import com.aurevia.authz.openfga.RelationshipAuthorizationPort.RelationshipCheck;
import com.aurevia.authz.policy.RuntimePolicyService;
import com.aurevia.authz.semantics.AuthorizationSemanticsRegistry;
import com.aurevia.authz.semantics.ResourceObjectKey;
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
    if(queries.runtimeResourceActionEnabled(request.resource(),request.action())
        .filter(enabled->!enabled).isPresent()) {
      long latency=(System.nanoTime()-started)/1_000_000;
      String reason="RESOURCE_DISABLED";
      auditor.record(new AuthorizationDecisionAuditor.Record(decisionId,request.subjectId(),
          request.resource(),request.action(),permission,false,false,false,reason,latency,
          request.correlationId(),List.of()));
      return new CheckEvaluation(new Decision("DENY",reason,"configured-model",decisionId,
          Map.of()),permission,0);
    }
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
    List<UiModuleDefinition> modules=panels.stream()
        .map(panel->uiModule(panel,permissions)).filter(Objects::nonNull).toList();
    List<PanelSummary> publicPanels=panels.stream().map(AuthorizationDecisionService::panelSummary)
        .toList();
    String version=manifestVersion(publicPanels,permissions,tree,modules);
    Instant generated=Instant.now();
    return new Manifest("EFFECTIVE_USER_MANIFEST",
        new SubjectView("user",issuer,subjectId),version,generated.plusSeconds(60),
        publicPanels,permissions,tree,new UiCatalog(version,generated,"1.0",modules));
  }

  private UiModuleDefinition uiModule(AuthorizationQueryRepository.PanelRecord panel,
      Map<String,List<String>> permissions) {
    try {
      JsonNode manifest=json.readTree(panel.manifestJson());
      List<UiRoute> routes=new ArrayList<>();
      Set<String> routeIds=new HashSet<>();
      for(JsonNode route:manifest.path("routes")) {
        String resource=preferredText(route,"resourceKey","resource");
        String action=route.path("action").asText("view");
        if(resource==null||!permissions.getOrDefault(resource,List.of()).contains(action)) continue;
        String routeId=preferredText(route,"key","id");
        if(routeId==null)continue;
        String path=route.path("path").asText().replaceFirst("^/+","");
        routes.add(new UiRoute(routeId,path,
            textOrDefault(route,"title",routeId),resource,action));
        routeIds.add(routeId);
      }
      if(routes.isEmpty()) return null;
      Map<String,AuthorizationQueryRepository.MenuOverride> overrides=
          queries.menuOverrides(panel.id()).stream().collect(Collectors.toMap(
              AuthorizationQueryRepository.MenuOverride::menuId,value->value));
      Map<String,NavigationCandidate> candidates=new LinkedHashMap<>();
      JsonNode declaredNavigation=manifest.path("navigation");
      if(declaredNavigation.isArray())for(JsonNode item:declaredNavigation) {
        String key=preferredText(item,"key","id");
        if(key==null)continue;
        String type=item.path("type").asText("PAGE").toUpperCase();
        String page=preferredText(item,"pageKey","routeId");
        var override=overrides.get(key);
        candidates.put(key,new NavigationCandidate(key,type,
            preferredText(item,"parentKey","parentId"),page,
            override!=null&&override.title()!=null?override.title():textOrDefault(item,"title",key),
            override!=null&&override.icon()!=null?override.icon():textOrNull(item,"icon"),
            override!=null&&override.sortOrder()!=null?override.sortOrder():item.path("order").asInt(0),
            textOrNull(item,"externalUrl"),"MANIFEST",override!=null&&override.hidden()));
      }
      if(candidates.isEmpty())for(JsonNode item:manifest.path("menus")) {
        String key=item.path("id").asText();
        var override=overrides.get(key);
        candidates.put(key,new NavigationCandidate(key,"PAGE",textOrNull(item,"parentId"),
            item.path("routeId").asText(),
            override!=null&&override.title()!=null?override.title():item.path("title").asText(),
            override!=null&&override.icon()!=null?override.icon():textOrNull(item,"icon"),
            override!=null&&override.sortOrder()!=null?override.sortOrder():item.path("order").asInt(0),
            null,"MANIFEST",override!=null&&override.hidden()));
      }
      overrides.values().stream().filter(value->"ADMIN".equals(value.source())).forEach(value->
          candidates.put(value.menuId(),new NavigationCandidate(value.menuId(),value.nodeType(),
              value.parentKey(),value.pageKey(),value.title(),value.icon(),
              value.sortOrder()==null?0:value.sortOrder(),value.externalUrl(),"ADMIN",value.hidden())));
      Map<String,NavigationCandidate> visible=new LinkedHashMap<>();
      candidates.values().stream().filter(item->!item.hidden())
          .filter(item->!"PAGE".equals(item.type())||routeIds.contains(item.pageKey()))
          .forEach(item->visible.put(item.key(),item));
      boolean changed;
      do {
        changed=visible.values().removeIf(item->item.parentKey()!=null
            &&!visible.containsKey(item.parentKey()));
      } while(changed);
      do {
        Set<String> parents=visible.values().stream().map(NavigationCandidate::parentKey)
            .filter(Objects::nonNull).collect(Collectors.toSet());
        changed=visible.values().removeIf(item->"GROUP".equals(item.type())
            &&!parents.contains(item.key()));
      } while(changed);
      List<UiNavigation> navigation=visible.values().stream()
          .sorted(Comparator.comparingInt(NavigationCandidate::order))
          .map(item->new UiNavigation(item.key(),item.type(),item.parentKey(),item.pageKey(),
              item.title(),item.icon(),item.order(),item.externalUrl(),item.source())).toList();
      List<UiMenu> menus=new ArrayList<>(navigation.stream().filter(item->"PAGE".equals(item.type()))
          .map(item->new UiMenu(item.key(),item.parentKey(),item.pageKey(),item.title(),
              item.icon(),item.order())).toList());
      menus.sort(Comparator.comparingInt(UiMenu::order));
      String declaredDefault=textOrNull(manifest,"defaultRouteId");
      String defaultRouteId=routeIds.contains(declaredDefault)?declaredDefault:
          routeIds.contains(panel.defaultRouteId())?panel.defaultRouteId():routes.getFirst().id();
      String apiBasePath=manifest.path("runtime").path("apiBasePath")
          .asText("/api/proxy/"+panel.serviceSlug());
      var remote=new RemoteDescriptor(panel.remoteEntryUrl(),panel.artifactRemoteName(),
          panel.artifactExposedModule(),panel.artifactContractVersion(),panel.artifactVersion(),
          panel.artifactIntegrity());
      String declaredModuleKey=manifest.path("module").path("key").asText(panel.slug());
      return new UiModuleDefinition(panel.id(),declaredModuleKey,panel.nameFa(),panel.nameEn(),
          panel.description(),panel.icon(),panel.sortOrder(),
          panel.routeBasePath().replaceFirst("^/",""),defaultRouteId,remote,
          new RuntimeDescriptor(apiBasePath),List.copyOf(routes),List.copyOf(menus),navigation,
          panel.classification(),panel.resourceDefinitionMode());
    } catch(Exception failure) {
      throw new IllegalStateException("stored UI manifest is invalid",failure);
    }
  }

  private static String textOrNull(JsonNode parent,String field) {
    JsonNode value=parent.get(field);
    return value==null||value.isNull()||value.asText().isBlank()?null:value.asText();
  }

  private static String preferredText(JsonNode parent,String preferred,String legacy) {
    String value=textOrNull(parent,preferred);return value==null?textOrNull(parent,legacy):value;
  }

  private static String textOrDefault(JsonNode parent,String field,String fallback) {
    String value=textOrNull(parent,field);return value==null?fallback:value;
  }

  private static PanelSummary panelSummary(AuthorizationQueryRepository.PanelRecord panel) {
    return new PanelSummary(panel.id(),panel.code(),panel.slug(),panel.nameFa(),panel.nameEn(),
        panel.remoteEntryUrl(),panel.artifactExposedModule(),panel.routeBasePath(),
        panel.artifactVersion(),panel.artifactContractVersion(),panel.artifactIntegrity(),
        panel.classification(),panel.resourceDefinitionMode());
  }

  private record NavigationCandidate(String key,String type,String parentKey,String pageKey,
      String title,String icon,int order,String externalUrl,String source,boolean hidden) {}

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
