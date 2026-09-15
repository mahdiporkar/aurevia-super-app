package com.aurevia.authz.directory;

import java.net.URI;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.Control;
import javax.naming.ldap.InitialLdapContext;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.PagedResultsControl;
import javax.naming.ldap.PagedResultsResponseControl;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** Paged, fail-closed service-account synchronization. User passwords never enter this service. */
@Component
public final class ActiveDirectorySyncJob {
  private final ActiveDirectorySyncRepository directory;
  private final OuAccessService access;
  private final TransactionTemplate transactions;
  private final boolean enabled;
  private final String url;
  private final String baseDn;
  private final String bindDn;
  private final String bindPassword;
  private final String issuer;
  private final int pageSize;
  private final double maxRemovalRatio;

  public ActiveDirectorySyncJob(ActiveDirectorySyncRepository directory,OuAccessService access,
      TransactionTemplate transactions,
      @Value("${aurevia.directory.enabled:false}") boolean enabled,
      @Value("${aurevia.directory.url:}") String url,
      @Value("${aurevia.directory.base-dn:}") String baseDn,
      @Value("${aurevia.directory.bind-dn:}") String bindDn,
      @Value("${aurevia.directory.bind-password:}") String bindPassword,
      @Value("${aurevia.directory.issuer:}") String issuer,
      @Value("${aurevia.directory.page-size:500}") int pageSize,
      @Value("${aurevia.directory.max-removal-ratio:0.25}") double maxRemovalRatio,
      @Value("${aurevia.directory.require-tls:false}") boolean requireTls) {
    this.directory=directory;this.access=access;this.transactions=transactions;
    this.enabled=enabled;this.url=url;this.baseDn=baseDn;this.bindDn=bindDn;
    this.bindPassword=bindPassword;this.issuer=issuer;this.pageSize=pageSize;
    this.maxRemovalRatio=maxRemovalRatio;
    if(enabled) validateConfiguration(requireTls);
  }

  @Scheduled(fixedDelayString="${aurevia.directory.sync-interval-ms:300000}")
  public void synchronize() {
    if(!enabled) return;
    UUID run=UUID.randomUUID();
    directory.startRun(run);
    int discoveredOus=0;
    int discoveredUsers=0;
    InitialLdapContext ldap=null;
    try {
      ldap=context();
      List<OuObservation> ous=readOus(ldap);
      List<UserObservation> users=readUsers(ldap);
      discoveredOus=ous.size();discoveredUsers=users.size();
      requireCompleteSnapshot("organizational units",discoveredOus);
      requireCompleteSnapshot("users",discoveredUsers);
      applySnapshot(ous,users);
      directory.completeRun(run,discoveredOus,discoveredUsers);
    } catch(Exception failure) {
      String safe=failure.getClass().getSimpleName()+": directory synchronization failed";
      directory.failRun(run,discoveredOus,discoveredUsers,safe);
    } finally {
      if(ldap!=null) try { ldap.close(); } catch(Exception ignored) { /* best effort */ }
    }
  }

  private List<OuObservation> readOus(InitialLdapContext ldap) throws Exception {
    List<OuObservation> result=new ArrayList<>();
    pagedSearch(ldap,"(objectClass=organizationalUnit)",
        new String[]{"distinguishedName","objectGUID","ou","name"},entry->{
          try {
            Attributes attributes=entry.getAttributes();
            String dn=value(attributes,"distinguishedName",entry.getNameInNamespace());
            result.add(new OuObservation(guid(attributes.get("objectGUID")),dn,
                DirectoryDnParser.parseUser("CN=_sync_,"+dn)));
          } catch(Exception error) { throw new DirectoryReadException(error); }
        });
    return result;
  }

  private List<UserObservation> readUsers(InitialLdapContext ldap) throws Exception {
    List<UserObservation> result=new ArrayList<>();
    pagedSearch(ldap,"(&(objectCategory=person)(objectClass=user))",
        new String[]{"distinguishedName","objectGUID","sAMAccountName"},entry->{
          try {
            Attributes attributes=entry.getAttributes();
            result.add(new UserObservation(guid(attributes.get("objectGUID")),
                value(attributes,"distinguishedName",entry.getNameInNamespace())));
          } catch(Exception error) { throw new DirectoryReadException(error); }
        });
    return result;
  }

  private void applySnapshot(List<OuObservation> ous,List<UserObservation> users) {
    Set<String> observedOus=new HashSet<>(ous.stream().map(OuObservation::externalId).toList());
    Map<String,UUID> ouIds=new HashMap<>();
    transactions.executeWithoutResult(ignored->{
      rejectLargeRemoval(directory.ouRemovalStats(issuer,observedOus));
      for(OuObservation ou:ous) {
        ouIds.put(DirectoryDnParser.canonicalDn(ou.dn()).toLowerCase(Locale.ROOT),upsertOu(ou));
      }
      for(var entry:ouIds.entrySet()) {
        try {
          String parent=parentOuDn(entry.getKey());
          directory.updateOuParent(entry.getValue(),
              parent==null?null:ouIds.get(parent.toLowerCase(Locale.ROOT)));
        } catch(Exception error) { throw new DirectoryReadException(error); }
      }
      directory.deactivateMissingOus(issuer,observedOus);
    });

    Set<String> observedUsers=new HashSet<>();
    for(UserObservation user:users) {
      observedUsers.add(user.externalId());
      List<UUID> linked=directory.linkedUsers(issuer,user.externalId());
      if(!linked.isEmpty()) access.syncDirectoryObservation(
          linked.getFirst(),issuer,user.dn(),null);
    }
    List<UUID> removedUsers=transactions.execute(status->{
      rejectLargeRemoval(directory.userRemovalStats(issuer,observedUsers));
      return directory.deactivateMissingUserAssignments(issuer,observedUsers);
    });
    if(removedUsers!=null) removedUsers.forEach(access::recalculateUser);
  }

  private void rejectLargeRemoval(ActiveDirectorySyncRepository.RemovalStats removal) {
    if(removal.active()>0 && ((double)removal.missing()/removal.active())>maxRemovalRatio) {
      throw new IllegalStateException("Directory removal safety threshold exceeded");
    }
  }

  private void pagedSearch(InitialLdapContext ldap,String filter,String[] attributes,
      Consumer<SearchResult> consumer) throws Exception {
    byte[] cookie=null;
    do {
      ldap.setRequestControls(new Control[]{new PagedResultsControl(pageSize,cookie,Control.CRITICAL)});
      SearchControls controls=new SearchControls();
      controls.setSearchScope(SearchControls.SUBTREE_SCOPE);
      controls.setReturningAttributes(attributes);
      NamingEnumeration<SearchResult> page=ldap.search(baseDn,filter,controls);
      try { while(page.hasMore()) consumer.accept(page.next()); }
      finally { page.close(); }
      cookie=null;
      Control[] responseControls=ldap.getResponseControls();
      if(responseControls!=null) for(Control control:responseControls) {
        if(control instanceof PagedResultsResponseControl response) cookie=response.getCookie();
      }
    } while(cookie!=null && cookie.length>0);
  }

  private InitialLdapContext context() throws Exception {
    Hashtable<String,String> environment=new Hashtable<>();
    environment.put(Context.INITIAL_CONTEXT_FACTORY,"com.sun.jndi.ldap.LdapCtxFactory");
    environment.put(Context.PROVIDER_URL,url);
    environment.put(Context.SECURITY_AUTHENTICATION,"simple");
    environment.put(Context.SECURITY_PRINCIPAL,bindDn);
    environment.put(Context.SECURITY_CREDENTIALS,bindPassword);
    environment.put("com.sun.jndi.ldap.connect.timeout","5000");
    environment.put("com.sun.jndi.ldap.read.timeout","10000");
    return new InitialLdapContext(environment,null);
  }

  private UUID upsertOu(OuObservation observation) {
    return directory.upsertOu(issuer,observation.externalId(),
        DirectoryDnParser.canonicalDn(observation.dn()),observation.parsed().ouPath(),
        observation.parsed().ouName());
  }

  private void validateConfiguration(boolean requireTls) {
    if(pageSize<1 || pageSize>5000 || maxRemovalRatio<0 || maxRemovalRatio>1
        || blank(url)||blank(baseDn)||blank(bindDn)||blank(bindPassword)||blank(issuer)) {
      throw new IllegalStateException("Directory synchronization configuration is incomplete");
    }
    URI parsed=URI.create(url);
    if(requireTls && !"ldaps".equalsIgnoreCase(parsed.getScheme())) {
      throw new IllegalStateException("Directory synchronization requires LDAPS");
    }
  }

  private static void requireCompleteSnapshot(String type,int count) {
    if(count==0) throw new IllegalStateException("Directory returned no "+type);
  }
  private static boolean blank(String value) { return value==null||value.isBlank(); }
  private static String parentOuDn(String dn) throws Exception {
    LdapName name=new LdapName(dn);
    if(name.size()<2) return null;
    LdapName parent=(LdapName)name.getPrefix(name.size()-1);
    return parent.getRdns().stream().anyMatch(rdn->"OU".equalsIgnoreCase(rdn.getType()))
        ? parent.toString():null;
  }
  private static String value(Attributes attributes,String key,String fallback) throws Exception {
    return attributes.get(key)==null?fallback:String.valueOf(attributes.get(key).get());
  }
  private static String guid(Attribute attribute) throws Exception {
    if(attribute==null) throw new IllegalArgumentException("Directory object has no objectGUID");
    Object value=attribute.get();
    return value instanceof byte[] bytes
        ? Base64.getUrlEncoder().withoutPadding().encodeToString(bytes):String.valueOf(value);
  }

  private record OuObservation(String externalId,String dn,DirectoryDnParser.ParsedUserDn parsed) {}
  private record UserObservation(String externalId,String dn) {}
  private static final class DirectoryReadException extends RuntimeException {
    private DirectoryReadException(Throwable cause) { super(cause); }
  }
}
