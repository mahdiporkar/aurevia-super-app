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
}
