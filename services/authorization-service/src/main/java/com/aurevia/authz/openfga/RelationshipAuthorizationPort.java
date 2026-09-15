package com.aurevia.authz.openfga;

public interface RelationshipAuthorizationPort {
  boolean check(String user, String relation, String object);
  java.util.Map<RelationshipCheck,Boolean> checkBatch(java.util.List<RelationshipCheck> checks);
  void write(String user, String relation, String object);
  void delete(String user, String relation, String object);
  record RelationshipCheck(String user,String relation,String object) {}
}
