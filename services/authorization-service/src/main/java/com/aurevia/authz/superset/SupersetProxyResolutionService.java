package com.aurevia.authz.superset;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SupersetProxyResolutionService {
  private final SupersetProxyRepository mappings;

  public SupersetProxyResolutionService(SupersetProxyRepository mappings) {
    this.mappings=mappings;
  }

  public Map<String,Object> resolve(String publicInstanceCode) {
    return mappings.activeMapping(publicInstanceCode).orElseThrow(()->
        new ResponseStatusException(HttpStatus.NOT_FOUND,
            "Active Superset proxy mapping not found"));
  }

  public Map<String,Object> resolveIntegration(String instanceCode) {
    if(instanceCode==null||!instanceCode.matches("[a-z][a-z0-9-]{2,79}")) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid Superset integration code");
    }
    return mappings.activeMapping(instanceCode).or(()->mappings.activeInstance(instanceCode))
        .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,
            "Active Superset integration not found"));
  }
}
