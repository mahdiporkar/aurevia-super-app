package com.aurevia.authz.outbound;
import static com.aurevia.authz.outbound.OutboundModels.*;
import java.util.List;import java.util.Optional;import java.util.UUID;
public interface OutboundRegistryRepository {
 List<ConnectionView> connections();Optional<RuntimeConnectionView> runtimeConnection(String reference);
 void createConnection(UUID id,ConnectionCommand command,String baseUrl,String actor);
 int updateConnection(UUID id,ConnectionCommand command,String baseUrl,String actor);
 List<ProfileView> profiles(String search);Optional<ProfileView> profile(UUID id);
 Optional<RuntimeProfileView> runtimeProfile(UUID id);void createProfile(UUID id,ProfileCommand command,String actor);
 int updateProfile(UUID id,long version,ProfileCommand command,String actor);
 int updateProfileStatus(UUID id,long version,boolean active,String actor);List<UsageView> usage(UUID id);
}
