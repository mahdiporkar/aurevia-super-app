package com.aurevia.authz.docs;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Sanitized, executable examples. No real credential or token is ever documented here. */
final class ApiDocumentationExamples {
  private ApiDocumentationExamples() {}

  static Object forOperation(String key) {
    return switch (key) {
      case "AuthorizationController#checkBatch" -> List.of(check("application:aurevia/finance", "view"),
          check("resource:page/finance.payments", "approve"));
      default -> null;
    };
  }

  static Object forType(Class<?> type) {
    String owner = type.getEnclosingClass() == null ? "" : type.getEnclosingClass().getSimpleName() + ".";
    return switch (owner + type.getSimpleName()) {
      case "AccessAdminDtos.ResourceRequest" -> map(
          "resourceKey", "page:finance.payments", "type", "PAGE",
          "parentId", "15484e5a-4872-4bf2-a1fa-37a0c264bf9d",
          "nameFa", "پرداخت‌ها", "nameEn", "Payments", "ownerDomain", "finance",
          "classification", "INTERNAL", "source", "ADMIN",
          "metadata", map("route", "/finance/payments", "icon", "credit-card"));
      case "AccessAdminDtos.ActionRequest" -> map(
          "actionKey", "approve", "nameFa", "تأیید", "nameEn", "Approve");
      case "AccessAdminDtos.UserRequest" -> map(
          "issuer", "http://localhost:8180/realms/aurevia", "externalId", "8e3a7fd6-demo-user",
          "username", "ali.rezaei", "displayName", "علی رضایی", "email", "ali.rezaei@example.test");
      case "AccessAdminDtos.GrantRequest" -> map(
          "subjectType", "USER", "subjectId", "8a7c0a4e-10a9-4d23-813c-dad9fed81745",
          "resourceId", "15484e5a-4872-4bf2-a1fa-37a0c264bf9d",
          "actionId", "76bb2db0-0c21-449c-b244-79a6bd411716",
          "relation", "direct", "expiresAt", "2027-03-20T20:30:00Z");
      case "AuthorizationDtos.CheckRequest" -> check("resource:page/finance.payments", "view");
      case "IdentityAdminDtos.RoleRequest" -> map(
          "roleKey", "finance-approver", "nameFa", "تأییدکننده مالی", "nameEn", "Finance approver");
      case "IdentityAdminDtos.RoleAssignmentRequest" -> map(
          "subjectType", "USER", "subjectId", "8a7c0a4e-10a9-4d23-813c-dad9fed81745",
          "roleId", "8c51f553-f995-4d0b-93ac-9d3d4b056aaa", "expiresAt", "2027-03-20T20:30:00Z");
      case "IdentityAdminDtos.StatusRequest", "ProxyRouteDtos.StatusRequest",
          "OutboundRegistryDtos.StatusRequest" -> map("active", true);
      case "IdentitySyncDtos.LoginIdentityRequest" -> map(
          "issuer", "http://localhost:8180/realms/aurevia", "subject", "8e3a7fd6-demo-user",
          "username", "ali.rezaei", "displayName", "علی رضایی", "email", "ali.rezaei@example.test",
          "distinguishedName", "CN=Ali Rezaei,OU=Sales,DC=aurevia,DC=local",
          "ouExternalId", "OU=Sales,DC=aurevia,DC=local", "directoryExternalId", "object-guid-demo",
          "groups", List.of(map("externalId", "sales-users", "path", "/Sales/Users", "displayName", "کاربران فروش")),
          "attributes", map("department", "Sales", "employeeNumber", "10042"));
      case "OuAccessDtos.GroupRequest" -> map(
          "code", "sales-access", "name", "دسترسی واحد فروش",
          "description", "اعضای OU فروش", "ruleCombiner", "ANY_OF", "active", true);
      case "OuAccessDtos.RuleRequest" -> map(
          "ouId", "54c28dd8-b93a-41f8-a6de-939160d44fd3", "matchMode", "SUBTREE");
      case "OuAccessDtos.GrantRequest" -> map(
          "applicationId", "2b6a0a84-da5b-4795-b9e7-e4fd8a93a180",
          "accessGroupId", "3691d12f-253f-4bce-924c-e23dc8ff6b37");
      case "OutboundRegistryDtos.ConnectionRequest" -> map(
          "connectionRef", "connection://legacy/crm", "name", "Legacy CRM production",
          "baseUrl", "https://crm-operation.example.internal:8443", "tlsRequired", true,
          "active", true, "version", 0);
      case "OutboundRegistryDtos.ProfileRequest" -> map(
          "code", "legacy-crm-client-credentials", "name", "توکن CRM عملیاتی",
          "description", "OAuth2 client_credentials؛ مقدار راز خارج از دیتابیس نگهداری می‌شود",
          "authMode", "LEGACY_SERVICE_TOKEN", "tokenConnectionRef", "connection://legacy/crm",
          "tokenEndpointPath", "/oauth/token", "requestFormat", "OAUTH_CLIENT_CREDENTIALS",
          "credentialSecretRef", "secret://legacy/crm-oauth", "scope", "crm.read crm.write",
          "audience", "crm-api", "tokenResponsePointer", "/access_token",
          "expiresInResponsePointer", "/expires_in", "tokenTypeResponsePointer", "/token_type",
          "authorizationScheme", "Bearer", "credentialTransport", "INTERNAL_LEGACY_HEADER",
          "expirySkewSeconds", 30, "connectTimeoutMs", 3000, "responseTimeoutMs", 10000,
          "maxTokenResponseSize", 65536, "active", true);
      case "PanelDtos.PanelRequest" -> map(
          "code", "FINANCE", "nameFa", "سامانه مالی", "nameEn", "Finance",
          "description", "میکروفرانت عملیات مالی", "slug", "finance", "serviceSlug", "finance-micro",
          "remoteName", "finance", "defaultRouteId", "finance-home",
          "remoteEntry", "http://localhost:3002/remoteEntry.js", "exposedModule", "./App",
          "routeBasePath", "/finance", "semanticVersion", "1.4.0", "contractVersion", "1.0",
          "integrity", "sha384-AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", "active", true, "sortOrder", 20);
      case "ProxyRouteDtos.TargetRequest" -> map(
          "code", "finance-operation", "name", "Finance operation service",
          "description", "مقصد ثابت در Operation Gateway", "gatewayBaseUrl", "http://operation-gateway:80",
          "upstreamBasePath", "/finance", "environment", "OPERATION",
          "healthCheckPath", "/finance/actuator/health", "connectTimeoutMs", 3000,
          "responseTimeoutMs", 30000, "maxResponseSize", 10485760,
          "outboundAuthProfileId", "95dc9e52-7ca5-4ad9-858d-a2d78ae1e5bd", "active", true);
      case "ProxyRouteDtos.RouteRequest" -> map(
          "code", "finance-api", "panelId", "2b6a0a84-da5b-4795-b9e7-e4fd8a93a180",
          "serviceTargetId", "3691d12f-253f-4bce-924c-e23dc8ff6b37",
          "serviceSlug", "finance-micro", "pathPrefix", "/finance-micro/api",
          "stripPrefix", 1, "priority", 100, "allowedMethods", List.of("GET", "POST", "PUT"),
          "preserveHost", false, "retryEnabled", false, "maxRetries", 0, "active", true);
      case "ProxyRouteDtos.OperationRequest" -> map(
          "httpMethod", "POST", "pathPattern", "/payments/{id}/approve",
          "resourceKey", "page:finance.payments", "actionKey", "approve",
          "authorizationRequired", true, "dataPolicyKey", "finance-own-branch",
          "active", true, "maxBodyBytes", 1048576);
      case "ProxyRouteDtos.PreviewRequest" -> map(
          "routeId", "e2350172-118d-45bb-a51f-f681713996d1", "path", "/finance-micro/api/payments/42");
      case "ProxyRouteDtos.MatchRequest" -> map("method", "POST", "path", "/payments/42/approve");
      case "ResourceManifestDtos.DefinitionManifest" -> map(
          "application", "finance", "manifestVersion", "2026.09.1",
          "resources", List.of(
              map("key", "application:finance", "type", "APPLICATION", "nameFa", "سامانه مالی",
                  "nameEn", "Finance", "ownerDomain", "finance", "classification", "INTERNAL",
                  "actions", List.of("view", "manage"), "status", "ACTIVE",
                  "source", "MICROFRONT_MANIFEST", "metadata", map()),
              map("key", "page:finance.payments", "type", "PAGE",
                  "parent", "application:finance", "nameFa", "پرداخت‌ها", "nameEn", "Payments",
                  "ownerDomain", "finance", "classification", "INTERNAL",
                  "actions", List.of("view", "create", "approve", "reject"), "status", "ACTIVE",
                  "source", "MICROFRONT_MANIFEST", "metadata", map("route", "/finance/payments"))));
      case "SupersetAssetDtos.AssetRequest" -> map(
          "externalId", "dashboard:42", "assetType", "DASHBOARD", "title", "داشبورد فروش روزانه",
          "urlPath", "/superset/dashboard/42/", "ownerExternalId", "8e3a7fd6-designer",
          "published", true, "instanceCode", "operation-default");
      case "SupersetAssetDtos.AssetGrantRequest" -> map(
          "subjectType", "ACCESS_GROUP", "subjectId", "3691d12f-253f-4bce-924c-e23dc8ff6b37",
          "level", "VIEW");
      case "SupersetInstanceDtos.InstanceRequest" -> map(
          "code", "operation-default", "name", "Superset عملیاتی", "zone", "OPERATION",
          "baseUrl", "http://operation-superset:8088", "connectionRef", "connection://superset/operation-default",
          "authMode", "REMOTE_USER", "tlsRequired", false, "active", true, "version", 0);
      case "SupersetInstanceDtos.MappingRequest" -> map(
          "publicInstanceId", "d9771d2d-6239-4453-8167-c16a40bbbe7d",
          "operationInstanceId", "8f280a17-cd03-4d5e-a93b-9de192ca5cb3",
          "publicPath", "/reports-runtime", "isDefault", true, "active", true);
      case "UiPluginDtos.ArtifactRequest" -> map(
          "artifactVersion", "1.4.0", "remoteEntryUrl", "http://localhost:3002/remoteEntry.js",
          "remoteName", "finance", "exposedModule", "./App", "contractVersion", "1.0",
          "integrity", "sha384-AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
          "manifest", "{\"schemaVersion\":\"1.0\",\"moduleKey\":\"finance\",\"routes\":[{\"id\":\"payments\",\"path\":\"payments\",\"resource\":\"page:finance.payments\",\"action\":\"view\"}],\"menus\":[{\"id\":\"payments-menu\",\"routeId\":\"payments\",\"title\":\"پرداخت‌ها\"}]}");
      case "UiPluginDtos.MenuOverrideRequest" -> map(
          "title", "پرداخت‌های سازمان", "icon", "credit-card", "order", 30, "hidden", false);
      case "LogIngestionController.ApiIngest" -> map(
          "eventTime", Instant.parse("2026-09-05T08:30:00Z").toString(), "userId", "8e3a7fd6-demo-user",
          "actorType", "USER", "serviceName", "superapp-bff", "httpMethod", "GET",
          "routeTemplate", "/finance-micro/api/payments", "statusCode", 200, "durationMs", 84,
          "sourceIp", "192.0.2.10", "correlationId", "5e4ddf32-1e7e-4e20-a9f3-64de1c938f97",
          "authorizationResult", "ALLOW", "resourceType", "PAGE",
          "resourceId", "page:finance.payments", "businessAction", "view");
      default -> null;
    };
  }

  private static Map<String, Object> check(String resource, String action) {
    return map("subjectId", "8e3a7fd6-demo-user",
        "issuer", "http://localhost:8180/realms/aurevia", "resource", resource,
        "action", action, "context", map("ip", "192.0.2.10", "branch", "TEH-01"),
        "correlationId", "5e4ddf32-1e7e-4e20-a9f3-64de1c938f97");
  }

  private static Map<String, Object> map(Object... entries) {
    Map<String, Object> result = new LinkedHashMap<>();
    for (int index = 0; index < entries.length; index += 2) {
      result.put((String) entries[index], entries[index + 1]);
    }
    return result;
  }
}
