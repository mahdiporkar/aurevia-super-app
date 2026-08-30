package com.aurevia.bff.outboundauth;
import reactor.core.publisher.Mono;
public interface SecretResolver {
 Mono<ResolvedSecret> resolve(SecretReference reference);
 record SecretReference(String value){public SecretReference{if(value==null||!value.matches("secret://[A-Za-z0-9._/-]+"))throw new IllegalArgumentException("Invalid secret reference");}}
 final class ResolvedSecret {
  private final String username,password,clientId,clientSecret,version;
  public ResolvedSecret(String u,String p,String ci,String cs,String v){username=u;password=p;clientId=ci;clientSecret=cs;version=v;}
  public String username(){return username;} public String password(){return password;} public String clientId(){return clientId;} public String clientSecret(){return clientSecret;} public String version(){return version;}
  @Override public String toString(){return "ResolvedSecret[REDACTED]";}
 }
}
