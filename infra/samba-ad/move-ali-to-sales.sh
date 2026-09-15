#!/bin/sh
set -eu
: "${SAMBA_ADMIN_PASSWORD:?SAMBA_ADMIN_PASSWORD is required}"
samba-tool user move ali.accounting 'OU=Sales,OU=Employees' -U "Administrator%$SAMBA_ADMIN_PASSWORD"
