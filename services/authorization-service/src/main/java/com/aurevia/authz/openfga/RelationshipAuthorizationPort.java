package com.aurevia.authz.openfga;

public interface RelationshipAuthorizationPort {
  boolean check(String user, String relation, String object);
  void write(String user, String relation, String object);
  void delete(String user, String relation, String object);
}
