package com.aurevia.authz.superset;

import static com.aurevia.authz.superset.SupersetAssetModels.*;

import com.aurevia.authz.access.AccessAdministrationService;
import com.aurevia.authz.access.AccessModels.GrantCommand;
import com.aurevia.authz.access.AccessModels.GrantResult;
import com.aurevia.authz.identity.SubjectKey;
import com.aurevia.authz.observability.AuditTrail;
import com.aurevia.authz.openfga.RelationshipAuthorizationPort;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SupersetAssetService {
  private static final Map<String, String> LEVEL_ACTIONS = Map.of(
      "VIEW", "view", "EDIT", "update", "MANAGE", "admin");
  private final SupersetAssetRepository repository;
  private final RelationshipAuthorizationPort relationships;
  private final AccessAdministrationService access;
  private final AuditTrail auditTrail;

  public SupersetAssetService(SupersetAssetRepository repository,
      RelationshipAuthorizationPort relationships, AccessAdministrationService access,
      AuditTrail auditTrail) {
    this.repository = repository;
    this.relationships = relationships;
    this.access = access;
    this.auditTrail = auditTrail;
  }

  public List<AssetView> assets() { return repository.assets(); }
  public List<AssetGrantView> grants(UUID assetId) { return repository.grants(assetId); }
  public AccessOptions accessOptions() {
    return new AccessOptions(repository.grantSubjects(), List.of(
        new LevelOption("VIEW", "view", "مشاهده"),
        new LevelOption("EDIT", "update", "ویرایش"),
        new LevelOption("MANAGE", "admin", "مدیریت")));
  }

  public List<AssetView> assetsForSubject(String issuer, String subject, String instance) {
    return repository.publishedAssets(instance).stream()
        .filter(asset -> canView(issuer, subject, asset)).toList();
  }

  public RuntimeAccess accessForSubject(String issuer, String subject, String instance,
      String path, String method, String query, String assetType, String assetId) {
    String user = new SubjectKey(issuer, subject).openFgaUser();
    if (relationships.check(user, "can_manage", "application:aurevia")) {
      return new RuntimeAccess("ALLOW", "SUPERSET_ADMIN_ALLOWED");
    }
    List<AssetView> allowed = repository.publishedAssets(instance).stream()
        .filter(asset -> canView(issuer, subject, asset)).toList();
    boolean hinted = assetId != null && !assetId.isBlank();
    boolean matched = allowed.stream().anyMatch(asset -> matches(asset, path, query, assetType, assetId));
    boolean read = Set.of("GET", "HEAD", "OPTIONS").contains(method.toUpperCase(Locale.ROOT));
    boolean common = read && isCommonRuntimePath(path);
    boolean telemetry = "POST".equalsIgnoreCase(method) && path.startsWith("/superset/log");
    boolean dataQuery = "POST".equalsIgnoreCase(method) && path.startsWith("/api/v1/chart/data");
    boolean granted = !allowed.isEmpty()
        && ((read && matched) || common || telemetry || (dataQuery && hinted && matched));
    return new RuntimeAccess(granted ? "ALLOW" : "DENY",
        granted ? "SUPERSET_ASSET_ALLOWED" : "SUPERSET_ASSET_DENIED");
  }

  @Transactional
  public CreateResult create(AssetCommand raw, String actor) {
    String type = validateAssetType(raw.assetType());
    String externalId = validateExternalId(raw.externalId());
    String path = validateAssetPath(raw.urlPath());
    String instanceCode = blank(raw.instanceCode()) ? "operation-default" : raw.instanceCode();
    OperationInstance instance = repository.activeOperationInstance(instanceCode)
        .orElseThrow(() -> new IllegalArgumentException("Active operation Superset instance not found"));
    var existing = repository.existing(instance.id(), externalId, type);
    if (existing.isPresent()) {
      ExistingAsset item = existing.get();
      return new CreateResult(item.id(), item.resourceId(), item.resourceKey(), true);
    }
    UUID parentResourceId = repository.catalogParentId();
    UUID resourceId = UUID.randomUUID();
    UUID assetId = UUID.randomUUID();
    String key = "external_resource:superset/" + instanceCode + "/"
        + type.toLowerCase(Locale.ROOT) + "/" + externalId;
    AssetCommand command = new AssetCommand(externalId, type, raw.title(), path,
        raw.ownerExternalId(), raw.published(), instanceCode);
    repository.create(assetId, resourceId, parentResourceId, instance.id(), command, key);
    auditTrail.success("SUPERSET", "superset.resource.register", null, null, "superset_asset",
        assetId.toString(), raw.title(), "REGISTER", null,
        Map.of("externalId", externalId, "assetType", type, "published", raw.published()));
    return new CreateResult(assetId, resourceId, key, false);
  }

  @Transactional
  public GrantResult grant(UUID assetId, String subjectType, UUID subjectId, String level,
      String actor) {
    String normalizedLevel = level == null ? "" : level.toUpperCase(Locale.ROOT);
    String actionKey = LEVEL_ACTIONS.get(normalizedLevel);
    if (actionKey == null) throw new IllegalArgumentException("level must be VIEW, EDIT, or MANAGE");
    GrantTarget target = repository.grantTarget(assetId, actionKey)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
            "Superset asset or access level not found"));
    return access.grant(new GrantCommand(null, subjectType, subjectId, target.resourceId(),
        target.actionId(), null), actor);
  }

  @Transactional
  public void revoke(UUID assetId, UUID grantId, String actor) {
    if (!repository.grantBelongsToAsset(assetId, grantId)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Superset asset grant not found");
    }
    access.revoke(grantId, actor);
  }

  private boolean canView(String issuer, String subject, AssetView asset) {
    String object = "external_resource:"
        + asset.resourceKey().replaceFirst("^external_resource:", "").replace(':', '/');
    return relationships.check(new SubjectKey(issuer, subject).openFgaUser(), "can_view", object);
  }
  private static boolean isCommonRuntimePath(String path) {
    return path.equals("/") || path.startsWith("/login") || path.startsWith("/logout")
        || path.startsWith("/static/") || path.startsWith("/api/v1/me")
        || path.startsWith("/api/v1/security/csrf_token")
        || path.startsWith("/api/v1/menu/") || path.endsWith("/_info");
  }
  private static boolean matches(AssetView asset, String path, String query,
      String hintedType, String hintedId) {
    if (normalize(path).equals(normalize(asset.urlPath()))) return true;
    String id = asset.externalId();
    if (!blank(hintedId) && id.equals(hintedId)
        && (blank(hintedType) || asset.assetType().equalsIgnoreCase(hintedType))) return true;
    String kind = "DASHBOARD".equalsIgnoreCase(asset.assetType()) ? "dashboard" : "chart";
    String queryKey = "DASHBOARD".equalsIgnoreCase(asset.assetType()) ? "dashboard_id" : "slice_id";
    return path.matches(".*/" + kind + "/" + java.util.regex.Pattern.quote(id) + "/?$")
        || query.matches("(?:^|.*&)" + queryKey + "=" + java.util.regex.Pattern.quote(id) + "(?:&.*)?");
  }
  private static String validateAssetType(String value) {
    String type = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    if (!Set.of("DASHBOARD", "CHART").contains(type)) {
      throw new IllegalArgumentException("Invalid Superset asset type");
    }
    return type;
  }
  private static String validateExternalId(String value) {
    String id = value == null ? "" : value.trim();
    if (!id.matches("[A-Za-z0-9._-]{1,255}")) {
      throw new IllegalArgumentException("Invalid Superset asset id");
    }
    return id;
  }
  private static String validateAssetPath(String value) {
    String path = normalize(value);
    if (!path.startsWith("/") || path.contains("..") || path.contains("\\")
        || path.chars().anyMatch(Character::isISOControl)) {
      throw new IllegalArgumentException("Invalid Superset asset path");
    }
    return path;
  }
  private static String normalize(String path) {
    if (blank(path)) return "/";
    return path.length() > 1 && path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
  }
  private static boolean blank(String value) { return value == null || value.isBlank(); }
}
