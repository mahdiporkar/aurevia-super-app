package com.aurevia.authz.superset;

import static com.aurevia.authz.api.dto.SupersetInstanceDtos.*;

import java.util.List;
import java.util.UUID;

public interface SupersetInstanceRepository {
  List<InstanceView> instances();
  void insert(InstanceValue value,String actor);
  boolean update(UUID id,long expectedVersion,InstanceValue value,boolean active,String actor);
  List<MappingView> mappings();
  void clearDefaultMappings(String actor);
  UUID upsertMapping(UUID proposedId,UUID publicId,UUID operationId,String publicPath,
      boolean isDefault,boolean active,String actor);
  List<String> activeZones(UUID first,UUID second);

  record InstanceValue(UUID id,String code,String name,String zone,String baseUrl,
      String connectionRef,String authMode,boolean tlsRequired,boolean active) {}
}
