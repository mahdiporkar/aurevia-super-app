package com.aurevia.authz.directory;

import java.util.*;
import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.directory.*;
import javax.naming.ldap.InitialLdapContext;
import javax.naming.ldap.LdapName;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Optional service-account sync. User passwords never enter this service. */
@Component
public class ActiveDirectorySyncJob {
  private final JdbcClient db;private final OuAccessService access;private final boolean enabled;
  private final String url,baseDn,bindDn,bindPassword,issuer;
  public ActiveDirectorySyncJob(JdbcClient db,OuAccessService access,
      @Value("${aurevia.directory.enabled:false}") boolean enabled,
      @Value("${aurevia.directory.url:}") String url,@Value("${aurevia.directory.base-dn:}") String baseDn,
      @Value("${aurevia.directory.bind-dn:}") String bindDn,@Value("${aurevia.directory.bind-password:}") String bindPassword,
      @Value("${aurevia.directory.issuer:}") String issuer){this.db=db;this.access=access;this.enabled=enabled;this.url=url;this.baseDn=baseDn;this.bindDn=bindDn;this.bindPassword=bindPassword;this.issuer=issuer;}

  @Scheduled(fixedDelayString="${aurevia.directory.sync-interval-ms:300000}")
  public void synchronize(){if(!enabled)return;UUID run=UUID.randomUUID();db.sql("insert into directory_sync_run(id,source,status) values(:id,'ACTIVE_DIRECTORY','RUNNING')").param("id",run).update();int ous=0,users=0;InitialLdapContext ldap=null;try{ldap=context();
    Set<String> observed=new HashSet<>();Map<String,UUID> ouIds=new HashMap<>();SearchControls controls=new SearchControls();controls.setSearchScope(SearchControls.SUBTREE_SCOPE);controls.setReturningAttributes(new String[]{"distinguishedName","objectGUID","ou","name"});NamingEnumeration<SearchResult> results=ldap.search(baseDn,"(objectClass=organizationalUnit)",controls);while(results.hasMore()){SearchResult result=results.next();Attributes a=result.getAttributes();String dn=value(a,"distinguishedName",result.getNameInNamespace());String external=guid(a.get("objectGUID"));var parsed=DirectoryDnParser.parseUser("CN=_sync_,"+dn);observed.add(external);ouIds.put(DirectoryDnParser.canonicalDn(dn).toLowerCase(Locale.ROOT),upsertOu(external,dn,parsed));ous++;}
    for(var entry:ouIds.entrySet()){String parent=parentOuDn(entry.getKey());db.sql("update directory_ou set parent_ou_id=:parent where id=:id").param("id",entry.getValue()).param("parent",parent==null?null:ouIds.get(parent.toLowerCase(Locale.ROOT))).update();}
    db.sql("update directory_ou set active=false,updated_at=now(),version=version+1 where issuer=:issuer and active and external_id not in (select unnest(cast(:ids as text[])))").param("issuer",issuer).param("ids",observed.toArray(String[]::new)).update();
    controls.setReturningAttributes(new String[]{"distinguishedName","objectGUID","sAMAccountName"});results=ldap.search(baseDn,"(&(objectCategory=person)(objectClass=user))",controls);while(results.hasMore()){SearchResult result=results.next();Attributes a=result.getAttributes();String external=guid(a.get("objectGUID"));var linked=db.sql("select id from app_user where issuer=:issuer and directory_external_id=:external").param("issuer",issuer).param("external",external).query(UUID.class).list();if(!linked.isEmpty()){access.syncDirectoryObservation(linked.getFirst(),issuer,value(a,"distinguishedName",result.getNameInNamespace()),null);users++;}}
    db.sql("update directory_sync_run set status='SUCCEEDED',completed_at=now(),discovered_ous=:ous,discovered_users=:users where id=:id").param("id",run).param("ous",ous).param("users",users).update();
  }catch(Exception failure){String safe=failure.getClass().getSimpleName()+": directory synchronization failed";db.sql("update directory_sync_run set status='FAILED',completed_at=now(),discovered_ous=:ous,discovered_users=:users,safe_error=:error where id=:id").param("id",run).param("ous",ous).param("users",users).param("error",safe).update();}finally{if(ldap!=null)try{ldap.close();}catch(Exception ignored){}}}
  private InitialLdapContext context() throws Exception {Hashtable<String,String> env=new Hashtable<>();env.put(Context.INITIAL_CONTEXT_FACTORY,"com.sun.jndi.ldap.LdapCtxFactory");env.put(Context.PROVIDER_URL,url);env.put(Context.SECURITY_AUTHENTICATION,"simple");env.put(Context.SECURITY_PRINCIPAL,bindDn);env.put(Context.SECURITY_CREDENTIALS,bindPassword);env.put("com.sun.jndi.ldap.connect.timeout","5000");env.put("com.sun.jndi.ldap.read.timeout","10000");return new InitialLdapContext(env,null);}
  private UUID upsertOu(String external,String dn,DirectoryDnParser.ParsedUserDn parsed){return db.sql("insert into directory_ou(issuer,external_id,external_dn,external_path,name) values(:issuer,:external,:dn,:path,:name) on conflict(issuer,external_id) do update set external_dn=excluded.external_dn,external_path=excluded.external_path,name=excluded.name,active=true,last_synced_at=now(),updated_at=now(),version=directory_ou.version+1 returning id").param("issuer",issuer).param("external",external).param("dn",DirectoryDnParser.canonicalDn(dn)).param("path",parsed.ouPath()).param("name",parsed.ouName()).query(UUID.class).single();}
  private static String parentOuDn(String dn)throws Exception {LdapName name=new LdapName(dn);if(name.size()<2)return null;LdapName parent=(LdapName)name.getPrefix(name.size()-1);return parent.getRdns().stream().anyMatch(r->"OU".equalsIgnoreCase(r.getType()))?parent.toString():null;}
  private static String value(Attributes a,String key,String fallback)throws Exception{return a.get(key)==null?fallback:String.valueOf(a.get(key).get());}
  private static String guid(Attribute a)throws Exception {if(a==null)throw new IllegalArgumentException("directory object has no objectGUID");Object value=a.get();return value instanceof byte[] bytes?Base64.getUrlEncoder().withoutPadding().encodeToString(bytes):String.valueOf(value);}
}
