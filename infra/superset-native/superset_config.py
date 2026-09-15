"""Two native demo instances; the public instance exposes static assets only."""
import json
import os
from pathlib import Path
from flask_appbuilder.const import AUTH_DB, AUTH_REMOTE_USER

root = Path(os.environ["AUREVIA_SUPERSET_NATIVE_ROOT"])
zone = os.environ["AUREVIA_SUPERSET_ZONE"]
settings = json.loads((root / "settings.json").read_text())
SECRET_KEY = settings[zone]["secretKey"]
SQLALCHEMY_DATABASE_URI = "sqlite:///" + str(root / zone / "metadata.db")
if zone == "operation":
    SQLALCHEMY_EXAMPLES_URI = "sqlite:///" + str(root / "operation" / "analytics.db")
SESSION_COOKIE_NAME = "AUREVIA_NATIVE_SUPERSET_" + zone.upper()
SESSION_COOKIE_HTTPONLY = True
SESSION_COOKIE_SECURE = True
SESSION_COOKIE_SAMESITE = "Lax"
SESSION_COOKIE_PATH = "/"
ENABLE_PROXY_FIX = True
APPLICATION_ROOT = "/"
TALISMAN_ENABLED = False
PUBLIC_ROLE_LIKE = None
FEATURE_FLAGS = {"ENABLE_VIEWERS": False}
AUTH_TYPE = AUTH_REMOTE_USER if zone == "operation" else AUTH_DB
AUTH_REMOTE_USER_ENV_VAR = "REMOTE_USER"
AUTH_USER_REGISTRATION = zone == "operation"
AUTH_USER_REGISTRATION_ROLE = "Gamma"
# Superset 5 exempts chart-data POST by default. This session-based demo requires its CSRF.
WTF_CSRF_EXEMPT_LIST = ["superset.views.core.log", "superset.views.core.explore_json",
                        "superset.dashboards.api.cache_dashboard_screenshot"]
# SQLite is restricted to deterministic local demo data. Production uses an approved DWH.
PREVENT_UNSAFE_DB_CONNECTIONS = False

class TrustedNativeIngress:
    def __init__(self, application):
        self.application = application
        manifest = json.loads((root / "privacy-assets" / "manifest.json").read_text())
        filename = manifest["filename"]
        if Path(filename).name != filename:
            raise RuntimeError("Invalid native privacy asset name")
        self.privacy_path = "/static/assets/" + filename
        self.privacy_asset = (root / "privacy-assets" / filename).read_bytes()

    def __call__(self, environ, start_response):
        path = environ.get("PATH_INFO", "/")
        if zone == "public" and path != "/health" and not path.startswith("/static/"):
            start_response("404 Not Found", [("Content-Type", "text/plain")])
            return [b"Public Superset serves static assets only\n"]
        if zone == "operation":
            # The native HTTPS ingress alone knows this value. Gunicorn is loopback-only.
            supplied = environ.get("HTTP_X_AUREVIA_NATIVE_INGRESS", "")
            import hmac
            if not hmac.compare_digest(supplied, settings["ingressSecret"]):
                start_response("403 Forbidden", [("Content-Type", "text/plain")])
                return [b"Trusted native ingress required\n"]
            environ["REMOTE_USER"] = environ.get("HTTP_X_AUREVIA_SUBJECT", "")
        if path == self.privacy_path and environ.get("REQUEST_METHOD") in ("GET", "HEAD"):
            # Keep upstream wheel files intact. Only the reviewed Scarf component returns null.
            # The filename retains upstream's hash, so this override must never be cached.
            start_response("200 OK", [("Content-Type", "application/javascript"),
                                     ("Content-Length", str(len(self.privacy_asset))),
                                     ("Cache-Control", "no-store")])
            return [] if environ["REQUEST_METHOD"] == "HEAD" else [self.privacy_asset]
        return self.application(environ, start_response)

ADDITIONAL_MIDDLEWARE = [TrustedNativeIngress]
