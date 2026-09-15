package com.aurevia.authz.directory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.naming.InvalidNameException;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;

/** RFC 2253 parsing for untrusted directory names; commas and escapes are never split manually. */
public final class DirectoryDnParser {
  private DirectoryDnParser() {}

  public static ParsedUserDn parseUser(String rawDn) {
    try {
      LdapName dn = new LdapName(required(rawDn));
      List<Rdn> rdns = new ArrayList<>(dn.getRdns());
      List<String> names = new ArrayList<>();
      List<Rdn> ouRdns = new ArrayList<>();
      for (Rdn rdn : rdns) {
        if (rdn.size() == 0) throw new IllegalArgumentException("invalid distinguishedName");
        if ("OU".equalsIgnoreCase(rdn.getType())) {
          names.add(String.valueOf(rdn.getValue()));
          ouRdns.add(rdn);
        }
      }
      if (ouRdns.isEmpty()) throw new IllegalArgumentException("distinguishedName has no OU");
      // LdapName#getRdns returns least-significant-to-most-significant (domain to leaf).
      String path = "/" + String.join("/", names.stream().map(DirectoryDnParser::pathPart).toList());
      List<Rdn> parentAndDomain = new ArrayList<>();
      boolean removedLeaf = false;
      for (Rdn rdn : rdns) {
        if (!removedLeaf && !"DC".equalsIgnoreCase(rdn.getType())) {
          if ("OU".equalsIgnoreCase(rdn.getType()) && rdn.equals(ouRdns.get(ouRdns.size()-1))) {
            // handled below by retaining every OU and domain, excluding the user CN
          }
        }
        if ("OU".equalsIgnoreCase(rdn.getType()) || "DC".equalsIgnoreCase(rdn.getType())) parentAndDomain.add(rdn);
      }
      LdapName ouDn = new LdapName(parentAndDomain);
      return new ParsedUserDn(ouDn.toString(), path, names.get(names.size()-1));
    } catch (InvalidNameException invalid) {
      throw new IllegalArgumentException("invalid distinguishedName", invalid);
    }
  }

  public static String canonicalDn(String rawDn) {
    try { return new LdapName(required(rawDn)).toString(); }
    catch (InvalidNameException invalid) { throw new IllegalArgumentException("invalid LDAP DN", invalid); }
  }

  public static boolean isWithinSubtree(String candidateDn, String rootDn) {
    try {
      LdapName candidate = new LdapName(required(candidateDn));
      LdapName root = new LdapName(required(rootDn));
      return candidate.startsWith(root);
    } catch (InvalidNameException invalid) {
      throw new IllegalArgumentException("invalid LDAP DN", invalid);
    }
  }

  private static String required(String value) {
    if (value == null || value.isBlank() || value.length() > 2048 || value.indexOf('\0') >= 0)
      throw new IllegalArgumentException("invalid LDAP DN");
    return value.trim();
  }
  private static String pathPart(String value) { return value.replace("%", "%25").replace("/", "%2F"); }
  public record ParsedUserDn(String ouDn, String ouPath, String ouName) {}
}
