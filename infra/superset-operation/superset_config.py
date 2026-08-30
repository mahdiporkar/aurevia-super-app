import os
from flask_appbuilder.const import AUTH_REMOTE_USER


class AureviaRemoteUserMiddleware:
    """Trust the identity header only because Gateway is private to the BFF network."""

    def __init__(self, application):
        self.application = application

    def __call__(self, environ, start_response):
        subject = environ.get("HTTP_X_AUREVIA_SUBJECT")
        if subject:
            environ["REMOTE_USER"] = subject
        return self.application(environ, start_response)

SECRET_KEY = os.environ["SUPERSET_SECRET_KEY"]
SQLALCHEMY_DATABASE_URI = os.environ["SUPERSET_DATABASE_URI"]
SESSION_COOKIE_NAME = "AUREVIA_OPERATION_SUPERSET"
SESSION_COOKIE_HTTPONLY = True
SESSION_COOKIE_SECURE = os.getenv("SUPERSET_COOKIE_SECURE", "false").lower() == "true"
PUBLIC_ROLE_LIKE = None
FEATURE_FLAGS = {"ENABLE_VIEWERS": False}
AUTH_TYPE = AUTH_REMOTE_USER
AUTH_USER_REGISTRATION = True
AUTH_USER_REGISTRATION_ROLE = os.getenv("SUPERSET_REMOTE_USER_ROLE", "Gamma")
AUTH_REMOTE_USER_ENV_VAR = "REMOTE_USER"
ADDITIONAL_MIDDLEWARE = [AureviaRemoteUserMiddleware]
ENABLE_PROXY_FIX = True
APPLICATION_ROOT = "/reports-runtime"
# Superset 5 still calls root-relative /api/v1 and /superset endpoints from its
# frontend, so its distinct operation-session cookie must cover those paths.
SESSION_COOKIE_PATH = "/"
TALISMAN_ENABLED = False
SCARF_ANALYTICS = False
