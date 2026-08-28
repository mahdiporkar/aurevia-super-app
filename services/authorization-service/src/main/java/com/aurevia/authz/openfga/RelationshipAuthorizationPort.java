package com.aurevia.authz.openfga;
public interface RelationshipAuthorizationPort { boolean check(String user, String relation, String object); }
