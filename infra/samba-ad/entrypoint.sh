#!/bin/sh
set -eu
: "${SAMBA_ADMIN_PASSWORD:?SAMBA_ADMIN_PASSWORD is required}"
: "${SAMBA_TEST_USER_PASSWORD:?SAMBA_TEST_USER_PASSWORD is required}"
if [ ! -f /var/lib/samba/private/sam.ldb ]; then
  rm -f /etc/samba/smb.conf
  samba-tool domain provision --server-role=dc --use-rfc2307 \
    --realm=AUREVIA.TEST --domain=AUREVIA --host-name=dc1 \
    --adminpass="$SAMBA_ADMIN_PASSWORD" --dns-backend=SAMBA_INTERNAL
  for ou in Employees Accounting Sales IT; do
    case "$ou" in Employees) dn="OU=Employees";; *) dn="OU=$ou,OU=Employees";; esac
    samba-tool ou create "$dn" -U "Administrator%$SAMBA_ADMIN_PASSWORD"
  done
  samba-tool user create ali.accounting "$SAMBA_TEST_USER_PASSWORD" \
    --userou='OU=Accounting,OU=Employees' --given-name=Ali --surname=Accounting \
    -U "Administrator%$SAMBA_ADMIN_PASSWORD"
  samba-tool user create sara.sales "$SAMBA_TEST_USER_PASSWORD" \
    --userou='OU=Sales,OU=Employees' --given-name=Sara --surname=Sales \
    -U "Administrator%$SAMBA_ADMIN_PASSWORD"
  samba-tool user create reza.it "$SAMBA_TEST_USER_PASSWORD" \
    --userou='OU=IT,OU=Employees' --given-name=Reza --surname=IT \
    -U "Administrator%$SAMBA_ADMIN_PASSWORD"
  samba-tool user setexpiry Administrator --noexpiry -U "Administrator%$SAMBA_ADMIN_PASSWORD"
fi
exec samba -i --debug-stdout
