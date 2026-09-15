FROM alpine:3.22
RUN apk add --no-cache samba samba-dc krb5 && mkdir -p /var/lib/samba /run/samba
COPY infra/samba-ad/entrypoint.sh /usr/local/bin/aurevia-samba-entrypoint
RUN chmod 0755 /usr/local/bin/aurevia-samba-entrypoint
EXPOSE 53/tcp 53/udp 88/tcp 88/udp 135/tcp 137/udp 138/udp 139/tcp 389/tcp 389/udp 445/tcp 464/tcp 464/udp 636/tcp 3268/tcp 3269/tcp
ENTRYPOINT ["/usr/local/bin/aurevia-samba-entrypoint"]
