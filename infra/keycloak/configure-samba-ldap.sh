#!/bin/sh
set -eu
: "${KEYCLOAK_ADMIN:?}" "${KEYCLOAK_ADMIN_PASSWORD:?}" "${SAMBA_ADMIN_PASSWORD:?}"
KC=/opt/keycloak/bin/kcadm.sh
until "$KC" config credentials --server http://keycloak:8080 --realm master --user "$KEYCLOAK_ADMIN" --password "$KEYCLOAK_ADMIN_PASSWORD" >/dev/null 2>&1; do sleep 3; done
realm_id=$("$KC" get realms/aurevia --fields id --format csv --noquotes)
existing=$("$KC" get components -r aurevia -q name=samba-ad --fields id --format csv --noquotes 2>/dev/null || true)
if [ -z "$existing" ]; then
  "$KC" create components -r aurevia -s name=samba-ad -s providerId=ldap \
    -s providerType=org.keycloak.storage.UserStorageProvider -s parentId="$realm_id" \
    -s 'config.vendor=["ad"]' -s 'config.connectionUrl=["ldap://samba-ad:389"]' \
    -s 'config.usersDn=["OU=Employees,DC=aurevia,DC=test"]' \
    -s 'config.bindDn=["CN=Administrator,CN=Users,DC=aurevia,DC=test"]' \
    -s "config.bindCredential=[\"$SAMBA_ADMIN_PASSWORD\"]" \
    -s 'config.usernameLDAPAttribute=["sAMAccountName"]' -s 'config.rdnLDAPAttribute=["cn"]' \
    -s 'config.uuidLDAPAttribute=["objectGUID"]' -s 'config.userObjectClasses=["person, organizationalPerson, user"]' \
    -s 'config.editMode=["READ_ONLY"]' -s 'config.importEnabled=["true"]' -s 'config.syncRegistrations=["false"]' \
    -s 'config.searchScope=["2"]' -s 'config.useTruststoreSpi=["never"]'
fi
ldap_id=$("$KC" get components -r aurevia -q name=samba-ad --fields id --format csv --noquotes | head -n1)
for spec in 'distinguished-name:distinguishedName:distinguishedName' 'ldap-id:objectGUID:LDAP_ID' 'department:department:department' 'title:title:title' 'employee-type:employeeType:employeeType'; do
  name=${spec%%:*}; rest=${spec#*:}; ldap_attr=${rest%%:*}; user_attr=${rest#*:}
  found=$("$KC" get components -r aurevia -q parent="$ldap_id" -q name="$name" --fields id --format csv --noquotes 2>/dev/null || true)
  [ -n "$found" ] || "$KC" create components -r aurevia -s name="$name" -s providerId=user-attribute-ldap-mapper \
    -s providerType=org.keycloak.storage.ldap.mappers.LDAPStorageMapper -s parentId="$ldap_id" \
    -s "config.ldap.attribute=[\"$ldap_attr\"]" -s "config.user.model.attribute=[\"$user_attr\"]" \
    -s 'config.read.only=["true"]' -s 'config.always.read.value.from.ldap=["true"]'
done
client_id=$("$KC" get clients -r aurevia -q clientId=aurevia-bff --fields id --format csv --noquotes)
for claim in distinguishedName LDAP_ID department title employeeType; do
  found=$("$KC" get "clients/$client_id/protocol-mappers/models" -r aurevia --fields name --format csv --noquotes | grep -Fx "$claim" || true)
  [ -n "$found" ] || "$KC" create "clients/$client_id/protocol-mappers/models" -r aurevia \
    -s name="$claim" -s protocol=openid-connect -s protocolMapper=oidc-usermodel-attribute-mapper \
    -s "config.'user.attribute'=$claim" -s "config.'claim.name'=$claim" \
    -s "config.'jsonType.label'=String" -s "config.'id.token.claim'=true" -s "config.'access.token.claim'=true" -s "config.'userinfo.token.claim'=true"
done
"$KC" create "user-storage/$realm_id/$ldap_id/sync?action=triggerFullSync" -r aurevia >/dev/null
