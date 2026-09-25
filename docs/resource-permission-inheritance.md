# Resource permission scope and V80 rollout

Normal grants are local to their target object. Across `application`, `resource`, and
`external_resource`, viewer/creator/editor/deleter/sharer/exporter are direct relations.
Only `manager` inherits through `parent`; every `can_*` includes manager. Editor still
implies view on the same object. Group and role membership continue to resolve direct
grants normally. Explicit administrative actions, including bootstrap `admin`, still
map to manager and authorize the complete subtree.

Previously every `can_*` recursively inherited the same permission from its parent.
Additionally, Java mapped application mutations, resource share/export, and external
create/delete to manager. Removing normal inheritance alone would leave those ordinary
actions granting administrative subtree access. The model and Java action mappings now
use dedicated ordinary relations for all three object types.

Parent tuples remain `(user=parent object, relation=parent, object=child object)`.
`AuthorizationDecisionService` already computes permissions from OpenFGA checks and then
adds ancestors to `resourceTree` with empty `actions` unless independently authorized.
It does not add ancestor permissions. Panel discovery and `uiCatalog` continue to derive
from effective grants; a visible panel is not a grant on its pages or components.

## Existing installation rollout

This is an intentional authorization compatibility change: users who relied on ordinary
parent grants need explicit grants on the resources they should access. Do not replace
those ordinary grants with manager merely to preserve broad access.

1. Pause user traffic, grant changes, and all old authorization-service/outbox instances.
   Use a maintenance window; do not mix old and new model/Java versions.
2. Publish `infra/openfga/model.fga` to the existing store, for example with
   `fga model write --store-id <store-id> --file infra/openfga/model.fga`. Record the returned
   model ID and configure `OPENFGA_MODEL_ID` for every new authorization-service instance.
   OpenFGA model versions are immutable; editing the source file does not update a live model.
3. Start the updated service in maintenance isolation. Flyway applies
   `V80__scope_normal_permissions_to_resource.sql` after either the historical migrations
   or the B79 fresh-install baseline. V80 remaps stored ordinary manager grants by action,
   increments their version, and rewrites unprocessed grant events (including retries and
   dead letters) to their new relation. Processed event history is retained.
4. Resolve pending/dead-letter events and let the outbox settle. Using the existing internal
   operations authentication, call
   `POST /internal/v1/registry/operations/openfga-reconcile?repair=false`, review the diff,
   then call the same endpoint with `repair=true`. This reconciles the entire authoritative
   store: new ordinary tuples are written and obsolete manager tuples removed. A manager
   tuple remains when another active explicit administrative grant requires it. Repeat the
   dry run and require zero missing/unexpected tuples before reopening traffic.
5. Expire authorization decision caches and BFF effective-context caches (or allow their
   configured TTLs to elapse while traffic is paused). Tuple repairs bump the authorization
   graph epoch, but a model-only change with no tuple repairs must also discard old decisions.
   Refresh client context. Verify a leaf-only viewer, a parent-only viewer, and the bootstrap
   administrator before resuming traffic.

No store reset, parent reversal, ancestor grant, or bootstrap rerun is required. Existing
parent and group/role tuples remain valid. Deploying only the new model is insufficient for
legacy ordinary grants already represented as manager. External/manual tuples outside the
database authority must be reviewed before full reconciliation, which removes unexpected
tuples. Reverting only Java or the model is unsafe after migration; rollback requires a
coordinated database/model/projection restore and intentionally restores the old broad access.

Fresh installs use the same canonical model through `tools/openfga-bootstrap.mjs` and apply
V80 automatically. The integration JSON fixture is generated from that model:

```sh
fga model transform --file infra/openfga/model.fga
fga model test --tests infra/openfga/model-tests.yaml
```

The model tests cover all six ordinary relations on all three object families, leaf/sibling/
component isolation, and direct plus group-role manager inheritance across mixed-type trees.
Java tests cover action mapping, navigation-only ancestors and route filtering, real grant
projection, V79-to-V80 migration, and the first administrator's seeded administrative subtree.

## Implementation and verification report

Changed source and test files:

| File | Change |
| --- | --- |
| [model.fga](../infra/openfga/model.fga) | Local ordinary relations; inherited manager on all three object families. |
| [AuthorizationSemanticsRegistry.java](../services/authorization-service/src/main/java/com/aurevia/authz/semantics/AuthorizationSemanticsRegistry.java) | Ordinary actions no longer map to manager. |
| [V80 migration](../services/authorization-service/src/main/resources/db/migration/V80__scope_normal_permissions_to_resource.sql) | Remap legacy grants and pending projection events. |
| [model-tests.yaml](../infra/openfga/model-tests.yaml) | 23 new scenarios for isolation and management inheritance. |
| [authorization-model.json](../services/authorization-service/src/test/resources/openfga/authorization-model.json) | Regenerated integration model from the canonical DSL. |
| [AuthorizationSemanticsRegistryTest.java](../services/authorization-service/src/test/java/com/aurevia/authz/semantics/AuthorizationSemanticsRegistryTest.java) | Updated ordinary-action expectations. |
| [AuthorizationDecisionServiceManifestTest.java](../services/authorization-service/src/test/java/com/aurevia/authz/authorization/AuthorizationDecisionServiceManifestTest.java) | Leaf permission, empty ancestor actions, sibling/component denial, panel and route filtering. |
| [PermissionLifecycleIntegrationTest.java](../services/authorization-service/src/test/java/com/aurevia/authz/sync/PermissionLifecycleIntegrationTest.java) | Real projected module-view and leaf-component isolation. |
| [FirstAdministratorBootstrapIntegrationTest.java](../services/authorization-service/src/test/java/com/aurevia/authz/bootstrap/FirstAdministratorBootstrapIntegrationTest.java) | Check all registered actions throughout the seeded Admin subtree. |
| [CoreBaselineInstallationIntegrationTest.java](../services/authorization-service/src/test/java/com/aurevia/authz/bootstrap/CoreBaselineInstallationIntegrationTest.java) | Fresh install expects B79 followed by V80. |
| [NormalPermissionMigrationIntegrationTest.java](../services/authorization-service/src/test/java/com/aurevia/authz/sync/NormalPermissionMigrationIntegrationTest.java) | V79 upgrade remaps ordinary actions, preserves explicit admin and processed history. |
| [verify-permission-integration-tests.mjs](../tools/verify-permission-integration-tests.mjs) | Require bootstrap, baseline, and migration tests to execute without skips. |

Documentation updates: this report, [permission operations](permission-definition-and-operation-fa.md),
[OpenFGA architecture](architecture-openfga-complete-fa.md),
[production guide](technical-team-zero-to-production-fa.md), and the V80 inventory marker in
[current state](current-state-fa.md).

Verification on 2026-09-25:

- OpenFGA CLI v0.7.20: model validates; 24 scenarios / 1,454 assertions pass.
- Authorization service: all 296 tests pass with zero skips against disposable PostgreSQL 16,
  Redis 7, and OpenFGA v1.18.1. The initial full run found one stale B79-only assertion;
  after updating it, all six baseline tests passed on the targeted rerun. All other 290
  tests passed in the full run, including all five first-administrator tests.
- BFF: 140 tests pass with zero skips.
- Integration execution guard, documentation validation, and `git diff --check` pass.

The source and tests are updated; no live store/model/tuple migration was performed.
The coordinated rollout above remains necessary for an existing deployment.
