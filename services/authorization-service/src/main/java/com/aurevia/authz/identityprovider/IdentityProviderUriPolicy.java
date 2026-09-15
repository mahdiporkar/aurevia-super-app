package com.aurevia.authz.identityprovider;

import java.net.InetAddress;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
class IdentityProviderUriPolicy {
  private final boolean allowHttp;
  private final boolean allowPrivateHosts;
  private final boolean requireAllowlist;
  private final List<String> allowedHosts;

  IdentityProviderUriPolicy(@Value("${aurevia.identity-providers.allow-http:false}") boolean allowHttp,
      @Value("${aurevia.identity-providers.allow-private-hosts:false}") boolean allowPrivateHosts,
      @Value("${aurevia.identity-providers.require-host-allowlist:true}") boolean requireAllowlist,
      @Value("${aurevia.identity-providers.allowed-hosts:}") String allowedHosts){
    this.allowHttp=allowHttp;this.allowPrivateHosts=allowPrivateHosts;
    this.requireAllowlist=requireAllowlist;
    this.allowedHosts=Arrays.stream(allowedHosts.split(",")).map(String::trim)
        .filter(value->!value.isBlank()).map(value->value.toLowerCase(Locale.ROOT)).toList();
  }

  String validate(String value,String field){
    try{
      URI uri=URI.create(value==null?"":value.trim());
      String scheme=uri.getScheme();String host=uri.getHost();
      if(host==null||uri.getUserInfo()!=null||uri.getFragment()!=null||uri.getRawQuery()!=null
          ||!("https".equalsIgnoreCase(scheme)||(allowHttp&&"http".equalsIgnoreCase(scheme)))) {
        throw new IllegalArgumentException(field+" is not an approved HTTP(S) URL");
      }
      String normalizedHost=host.toLowerCase(Locale.ROOT);
      if(requireAllowlist&&!matchesAllowlist(normalizedHost)) {
        throw new IllegalArgumentException(field+" host is not allowlisted");
      }
      if(!allowPrivateHosts) for(InetAddress address:InetAddress.getAllByName(host)) {
        if(address.isAnyLocalAddress()||address.isLoopbackAddress()||address.isLinkLocalAddress()
            ||address.isSiteLocalAddress()) throw new IllegalArgumentException(
                field+" resolves to a private address");
      }
      String normalized=uri.normalize().toString();
      return normalized.endsWith("/")?normalized.substring(0,normalized.length()-1):normalized;
    }catch(IllegalArgumentException invalid){throw invalid;}
    catch(Exception invalid){throw new IllegalArgumentException(field+" cannot be resolved",invalid);}
  }
  private boolean matchesAllowlist(String host){return allowedHosts.stream().anyMatch(pattern->
      pattern.startsWith("*.")?host.endsWith(pattern.substring(1))&&host.length()>pattern.length()-1
          :host.equals(pattern));}
}
