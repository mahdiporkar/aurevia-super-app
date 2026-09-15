package com.aurevia.authz.routing;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RouteResolutionService {
  private final RouteResolutionRepository routes;

  public RouteResolutionService(RouteResolutionRepository routes) { this.routes=routes; }

  public ResolvedRoute resolve(String path,String method) {
    final String canonical;
    try { canonical=RoutePathPolicy.path(path); }
    catch(IllegalArgumentException failure) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid route path");
    }
    String verb=method.toUpperCase(Locale.ROOT);
    List<RouteResolutionRepository.Candidate> matched=routes.activeCandidates(verb).stream()
        .filter(row->prefixMatch(canonical,row.normalizedPrefix()))
        .filter(row->Arrays.asList(row.allowedMethods().split(",")).contains(verb))
        .filter(row->RoutePathPolicy.matches(row.pathPattern(),relative(canonical,row.normalizedPrefix())))
        .sorted(Comparator
            .comparingInt((RouteResolutionRepository.Candidate row)->row.normalizedPrefix().length()).reversed()
            .thenComparing(Comparator.comparingInt(RouteResolutionRepository.Candidate::priority).reversed())
            .thenComparing(Comparator.comparingInt(
                (RouteResolutionRepository.Candidate row)->RoutePathPolicy.specificity(row.pathPattern())).reversed()))
        .toList();
    if(matched.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Active route operation not found");
    }
    var selected=matched.getFirst();
    if(matched.size()>1) {
      var second=matched.get(1);
      if(selected.normalizedPrefix().length()==second.normalizedPrefix().length()
          && selected.priority()==second.priority()
          && RoutePathPolicy.specificity(selected.pathPattern())
              ==RoutePathPolicy.specificity(second.pathPattern())) {
        throw new ResponseStatusException(HttpStatus.CONFLICT,"Ambiguous active route operations");
      }
    }
    return new ResolvedRoute(selected.routeId(),selected.operationId(),selected.panelId(),
        selected.panelSlug(),selected.routeKey(),selected.pathPrefix(),selected.targetId(),
        selected.targetKey(),selected.stripPrefix(),selected.rewritePattern(),
        selected.rewriteReplacement(),selected.resourceId(),selected.resourceKey(),
        selected.actionKey(),selected.authorizationRequired(),selected.dataPolicyKey(),
        selected.maxBodyBytes(),selected.connectTimeoutMs(),selected.responseTimeoutMs(),
        selected.maxResponseBytes(),selected.retryEnabled(),selected.maxRetries(),
        selected.tlsProfileRef(),selected.authProfileId(),selected.authMode(),
        selected.authProfileVersion(),selected.credentialTransport());
  }

  private static boolean prefixMatch(String path,String prefix) {
    String bare=prefix.equals("/")?"/":prefix.substring(0,prefix.length()-1);
    return path.equals(bare)||path.startsWith(prefix);
  }

  private static String relative(String path,String prefix) {
    String bare=prefix.equals("/")?"":prefix.substring(0,prefix.length()-1);
    String result=path.substring(bare.length());
    return result.isEmpty()?"/":result;
  }
}
