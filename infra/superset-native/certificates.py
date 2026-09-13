"""Short-lived demo CA, HTTPS server identity and BFF client identity."""
import datetime
import ipaddress
import json
import os
from pathlib import Path
import sys
from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import rsa
from cryptography.x509.oid import ExtendedKeyUsageOID, NameOID

root = Path(os.environ.get("AUREVIA_SUPERSET_NATIVE_ROOT", str(Path.home() / ".local/share/aurevia-superset-demo")))
host = ipaddress.ip_address(sys.argv[1])
directory = root / "tls"
directory.mkdir(mode=0o700, exist_ok=True)
state = directory / "identity.json"
if state.exists():
    if json.loads(state.read_text())["host"] != str(host):
        raise SystemExit("Demo certificate host changed; rotate demo identities and reload BFF explicitly")
    certificate = x509.load_pem_x509_certificate((directory / "server.pem").read_bytes())
    if certificate.not_valid_after_utc < datetime.datetime.now(datetime.timezone.utc):
        raise SystemExit("Demo certificate expired; rotate demo identities and reload BFF explicitly")
    print("Existing unexpired demo identities retained")
    raise SystemExit(0)

now = datetime.datetime.now(datetime.timezone.utc)
def key():
    return rsa.generate_private_key(public_exponent=65537, key_size=2048)
def save_key(name, value):
    path = directory / name
    path.write_bytes(value.private_bytes(serialization.Encoding.PEM, serialization.PrivateFormat.PKCS8,
                                        serialization.NoEncryption()))
    path.chmod(0o600)
def subject(name):
    return x509.Name([x509.NameAttribute(NameOID.ORGANIZATION_NAME, "Aurevia Local Demo"),
                      x509.NameAttribute(NameOID.COMMON_NAME, name)])
def builder(name, public_key, issuer):
    return x509.CertificateBuilder().subject_name(subject(name)).issuer_name(issuer).public_key(public_key)\
        .serial_number(x509.random_serial_number()).not_valid_before(now - datetime.timedelta(minutes=5))\
        .not_valid_after(now + datetime.timedelta(days=30))

ca_key = key()
ca = builder("Aurevia Native Superset Demo CA", ca_key.public_key(), subject("Aurevia Native Superset Demo CA"))\
    .add_extension(x509.BasicConstraints(ca=True, path_length=0), critical=True)\
    .add_extension(x509.KeyUsage(digital_signature=True, content_commitment=False, key_encipherment=False,
                                data_encipherment=False, key_agreement=False, key_cert_sign=True,
                                crl_sign=True, encipher_only=False, decipher_only=False), critical=True)\
    .sign(ca_key, hashes.SHA256())
save_key("ca-key.pem", ca_key)
(directory / "ca.pem").write_bytes(ca.public_bytes(serialization.Encoding.PEM))
for name, common_name, usage in (("server", str(host), ExtendedKeyUsageOID.SERVER_AUTH),
                                ("client", "aurevia-bff-native-demo", ExtendedKeyUsageOID.CLIENT_AUTH)):
    private = key()
    request = builder(common_name, private.public_key(), ca.subject)\
        .add_extension(x509.BasicConstraints(ca=False, path_length=None), critical=True)\
        .add_extension(x509.ExtendedKeyUsage([usage]), critical=True)
    if name == "server":
        request = request.add_extension(x509.SubjectAlternativeName([
            x509.IPAddress(host), x509.IPAddress(ipaddress.ip_address("127.0.0.1")),
            x509.DNSName("localhost"), x509.DNSName("host.docker.internal")]), critical=False)
    certificate = request.sign(ca_key, hashes.SHA256())
    save_key(name + "-key.pem", private)
    (directory / (name + ".pem")).write_bytes(certificate.public_bytes(serialization.Encoding.PEM))
state.write_text(json.dumps({"host": str(host), "expiresAt": (now + datetime.timedelta(days=30)).isoformat()}) + "\n")
print("Created demo HTTPS and BFF client identities; CA private key stays outside the project")
