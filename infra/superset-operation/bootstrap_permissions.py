"""Grant Superset datasource read capability to the remote-user role.

The Aurevia BFF remains the authority for which dashboard or chart a user may
request. Superset still needs datasource permission to render an otherwise
authorized dashboard after the remote-user session is established. This
bootstrap is idempotent: it grants ``datasource_access`` to the local read
role and gives explicitly configured report designers Superset's native
``Alpha`` role. Dashboard ownership is synchronized from the BFF's current
asset-level edit decision, so a revoked Aurevia grant removes the native edit
affordance on the next dashboard request.
"""

import os

def grant_dashboard_role(dashboards, role):
    """RBAC and datasource permissions must agree for approved demo dashboards."""
    for dashboard in dashboards:
        if role not in dashboard.roles:
            dashboard.roles.append(role)


def main() -> None:
    from superset.app import create_app
    from sqlalchemy import text

    app = create_app()
    from superset import db

    with app.app_context():
        from superset.connectors.sqla.models import SqlaTable
        from superset.models.dashboard import Dashboard

        security_manager = app.appbuilder.sm
        role = security_manager.find_role("Gamma")
        if role is None:
            raise RuntimeError("Superset role Gamma is missing")

        raw_ids = os.environ.get("SUPERSET_AUREVIA_DASHBOARD_IDS", "")
        dashboard_ids = [int(value.strip()) for value in raw_ids.split(",") if value.strip()]
        if not dashboard_ids:
            titles = [value.strip() for value in os.environ.get(
                "SUPERSET_AUREVIA_DASHBOARD_TITLES", "Sales Dashboard").split(",") if value.strip()]
            dashboard_ids = [dashboard.id for dashboard in db.session.query(Dashboard)
                             .filter(Dashboard.dashboard_title.in_(titles), Dashboard.published.is_(True)).all()]
        dashboards = db.session.query(Dashboard).filter(
            Dashboard.id.in_(dashboard_ids), Dashboard.published.is_(True)).all()
        if not dashboards or len(dashboards) != len(set(dashboard_ids)):
            raise RuntimeError("Approved published demo dashboards are missing")
        grant_dashboard_role(dashboards, role)

        rows = db.session.execute(
            text("""
                select distinct s.datasource_id
                from dashboard_slices ds
                join dashboards d on d.id=ds.dashboard_id and d.published
                join slices s on s.id=ds.slice_id
                where d.id = any(:dashboard_ids)
                  and s.datasource_id is not null
            """),
            {"dashboard_ids": dashboard_ids},
        )
        datasource_ids = [row[0] for row in rows]
        tables = (
            db.session.query(SqlaTable)
            .filter(SqlaTable.id.in_(datasource_ids))
            .all()
        )
        for table in tables:
            if not table.perm:
                continue
            permission_view = security_manager.add_permission_view_menu(
                "datasource_access", table.perm
            )
            security_manager.add_permission_role(role, permission_view)

        editor_role = security_manager.find_role("Alpha")
        if editor_role is None:
            raise RuntimeError("Superset role Alpha is missing")
        editor_usernames = {
            username.strip()
            for username in os.environ.get(
                "SUPERSET_REMOTE_EDIT_USERS", "administrator,report-designer"
            ).split(",")
            if username.strip()
        }
        editor_roles_added = []
        for username in editor_usernames:
            user = security_manager.find_user(username=username)
            if user is None:
                continue
            if editor_role not in user.roles:
                user.roles.append(editor_role)
                editor_roles_added.append(username)
        db.session.commit()
        print(
            f"Configured datasource read access for Gamma ({len(tables)} tables); "
            f"Superset editor roles updated: {', '.join(sorted(editor_roles_added)) or 'none'}"
        )


if __name__ == "__main__":
    main()
