package com.aurevia.authz.superset;

import static com.aurevia.authz.superset.SupersetAssetModels.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SupersetAssetRepository {
  List<AssetView> assets();
  List<AssetView> publishedAssets(String instanceCode);
  List<AssetGrantView> grants(UUID assetId);
  List<SubjectOption> grantSubjects();
  Optional<OperationInstance> activeOperationInstance(String code);
  Optional<ExistingAsset> existing(UUID instanceId, String externalId, String assetType);
  UUID catalogParentId();
  void create(UUID assetId, UUID resourceId, UUID parentResourceId, UUID instanceId, AssetCommand command,
      String resourceKey);
  Optional<GrantTarget> grantTarget(UUID assetId, String actionKey);
  boolean grantBelongsToAsset(UUID assetId, UUID grantId);
}
