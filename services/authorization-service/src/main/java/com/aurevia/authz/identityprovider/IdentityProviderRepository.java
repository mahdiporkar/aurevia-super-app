package com.aurevia.authz.identityprovider;

import static com.aurevia.authz.identityprovider.IdentityProviderModels.*;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IdentityProviderRepository {
  List<ProviderView> findAll();
  List<ProviderView> findEnabled();
  Optional<ProviderView> findEnabledByCode(String code);
  Optional<ProviderSnapshot> snapshot(UUID id);
  void insert(UUID id,ProviderCommand command,String actor);
  int update(UUID id,long version,ProviderCommand command,String actor);
  int updateEnabled(UUID id,long version,boolean enabled,String actor);
  void updateHealth(UUID id,String status,Instant checkedAt,String safeError);
}
