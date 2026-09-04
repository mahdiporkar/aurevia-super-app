package com.aurevia.authz.superset;

import java.util.Map;
import java.util.Optional;

public interface SupersetProxyRepository {
  Optional<Map<String,Object>> activeMapping(String publicInstanceCode);
}
