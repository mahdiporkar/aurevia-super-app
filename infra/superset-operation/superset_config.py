import os
import re
from flask import g
from flask_appbuilder.const import AUTH_REMOTE_USER
from superset.security.manager import SupersetSecurityManager


def _remote_edit_users():
    return {
        username.strip()
        for username in os.getenv("SUPERSET_REMOTE_EDIT_USERS", "administrator,report-designer").split(",")
        if username.strip()
    }


def _approved_dashboards():
    from superset import db
    from superset.models.dashboard import Dashboard

    raw_ids = os.getenv("SUPERSET_AUREVIA_DASHBOARD_IDS", "")
    dashboard_ids = [int(value.strip()) for value in raw_ids.split(",") if value.strip()]
    if dashboard_ids:
        return db.session.query(Dashboard).filter(
            Dashboard.id.in_(dashboard_ids), Dashboard.published.is_(True)).all()

    titles = [value.strip() for value in os.getenv(
        "SUPERSET_AUREVIA_DASHBOARD_TITLES", "Sales Dashboard").split(",") if value.strip()]
    return db.session.query(Dashboard).filter(
        Dashboard.dashboard_title.in_(titles), Dashboard.published.is_(True)).all()


def _reconcile_editor_dashboard_ownership(user):
    """Mirror the BFF edit decision for the published dashboard being requested."""
    from flask import has_request_context, request

    if not has_request_context():
        return False
    match = re.fullmatch(r"/superset/dashboard/(\d+)/?", request.path)
    if match is None:
        return False
    dashboard_id = int(match.group(1))
    from superset import db
    from superset.models.dashboard import Dashboard

    # An Aurevia administrator has a BFF-authorized edit decision for every
    # published native dashboard, including dashboards not yet catalogued as a
    # per-asset resource. Superset's dashboard-RBAC UI additionally requires
    # native ownership, so synchronize the requested dashboard by ID instead
    # of restricting administrators to the configured demo IDs.
    dashboard = db.session.query(Dashboard).filter(
        Dashboard.id == dashboard_id, Dashboard.published.is_(True)
    ).one_or_none()
    if dashboard is None:
        return False
    allowed = request.environ.get("AUREVIA_SUPERSET_EDIT_ALLOWED") == "1"
    if allowed and user not in dashboard.owners:
        dashboard.owners.append(user)
        return True
    if not allowed and user in dashboard.owners:
        dashboard.owners.remove(user)
        return True
    return False


def _request_allows_dashboard_edit():
    from flask import has_request_context, request

    return has_request_context() and request.environ.get(
        "AUREVIA_SUPERSET_EDIT_ALLOWED") == "1"


def _request_allows_aurevia_access():
    """Return true only for requests pre-authorized by the private BFF."""
    from flask import has_request_context, request

    return has_request_context() and request.environ.get(
        "AUREVIA_SUPERSET_ACCESS_ALLOWED") == "1"


def _install_aurevia_dashboard_access_filter(_app=None):
    """Let the private BFF grant access before Superset's native list filter.

    Superset's DashboardDAO applies DashboardAccessFilter before
    ``Dashboard.raise_for_access``. A dashboard granted only in Aurevia can
    therefore look like a 404 when its datasource is not in Gamma. The BFF
    has already checked the exact dashboard/API path, so bypass that native
    curation filter only for its private, server-added access header.
    """
    from superset.dashboards.filters import DashboardAccessFilter

    if getattr(DashboardAccessFilter, "_aurevia_wrapped", False):
        return
    native_apply = DashboardAccessFilter.apply

    def apply(self, query, value):
        if _request_allows_aurevia_access():
            return query
        return native_apply(self, query, value)

    DashboardAccessFilter.apply = apply
    DashboardAccessFilter._aurevia_wrapped = True


class AureviaSecurityManager(SupersetSecurityManager):
    """Give configured report designers the native Superset edit affordances."""

    def raise_for_access(self, *args, **kwargs):
        # The BFF is the per-user asset authorization boundary. Native Superset
        # only knows the shared Gamma role, so it would reject a dashboard that
        # Aurevia explicitly granted to one user and redirect to dashboard/list.
        # This bypass is available only on the private BFF-to-Superset hop; a
        # direct request without the signed-by-topology header uses native ACLs.
        if _request_allows_aurevia_access():
            return
        return super().raise_for_access(*args, **kwargs)

    def raise_for_ownership(self, resource):
        # The BFF performs the exact Aurevia EDIT/MANAGE check for this request
        # and adds the header only after that decision. Superset still enforces
        # ownership for direct or unapproved requests.
        current_user = getattr(g, "user", None)
        from superset.models.dashboard import Dashboard

        if isinstance(resource, Dashboard) and current_user is not None:
            from flask import request

            if request.environ.get("AUREVIA_SUPERSET_EDIT_ALLOWED") == "1":
                return
        return super().raise_for_ownership(resource)

    def auth_user_remote_user(self, username):
        user = super().auth_user_remote_user(username)
        if user is None:
            return user

        editor_role = self.find_role("Alpha")
        if editor_role is None:
            return user

        changed = False
        # A user granted Aurevia EDIT receives the native affordance for that
        # dashboard, even if they have only Gamma as their base Superset role.
        # The BFF still authorizes every write, so Alpha alone grants no access
        # to another dashboard or to a dashboard after its grant is revoked.
        if (
            username in _remote_edit_users() or _request_allows_dashboard_edit()
        ) and editor_role not in user.roles:
            user.roles.append(editor_role)
            changed = True
        changed = _reconcile_editor_dashboard_ownership(user) or changed
        if not changed:
            return user
        from superset import db

        db.session.commit()
        return user


CUSTOM_SECURITY_MANAGER = AureviaSecurityManager
FLASK_APP_MUTATOR = _install_aurevia_dashboard_access_filter


class AureviaRemoteUserMiddleware:
    """Trust the identity header only because Gateway is private to the BFF network."""

    def __init__(self, application):
        self.application = application

    def __call__(self, environ, start_response):
        subject = environ.get("HTTP_X_AUREVIA_SUBJECT")
        if subject:
            environ["REMOTE_USER"] = subject
        if environ.get("HTTP_X_AUREVIA_SUPERSET_EDIT") == "true":
            environ["AUREVIA_SUPERSET_EDIT_ALLOWED"] = "1"
        if environ.get("HTTP_X_AUREVIA_SUPERSET_ACCESS") == "true":
            environ["AUREVIA_SUPERSET_ACCESS_ALLOWED"] = "1"
        return self.application(environ, start_response)

SECRET_KEY = os.environ["SUPERSET_SECRET_KEY"]
SQLALCHEMY_DATABASE_URI = os.environ["SUPERSET_DATABASE_URI"]
SESSION_COOKIE_NAME = "AUREVIA_OPERATION_SUPERSET"
SESSION_COOKIE_HTTPONLY = True
SESSION_COOKIE_SECURE = os.getenv("SUPERSET_COOKIE_SECURE", "false").lower() == "true"
PUBLIC_ROLE_LIKE = None
FEATURE_FLAGS = {"ENABLE_VIEWERS": False, "DASHBOARD_RBAC": True}
AUTH_TYPE = AUTH_REMOTE_USER
AUTH_USER_REGISTRATION = True
AUTH_USER_REGISTRATION_ROLE = os.getenv("SUPERSET_REMOTE_USER_ROLE", "Gamma")
AUTH_REMOTE_USER_ENV_VAR = "REMOTE_USER"
ADDITIONAL_MIDDLEWARE = [AureviaRemoteUserMiddleware]
ENABLE_PROXY_FIX = True
# Superset runs at its native root internally. Nginx and the Java BFF own the
# public routing boundary; nesting the Superset 5 SPA below APPLICATION_ROOT
# prevents its dashboard router from mounting reliably.
APPLICATION_ROOT = "/"
# Superset 5 still calls root-relative /api/v1 and /superset endpoints from its
# frontend, so its distinct operation-session cookie must cover those paths.
SESSION_COOKIE_PATH = "/"
TALISMAN_ENABLED = False
SCARF_ANALYTICS = False
