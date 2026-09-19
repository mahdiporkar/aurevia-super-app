"""Grant Superset datasource read capability to the remote-user role.

The Aurevia BFF remains the authority for which dashboard or chart a user may
request. Superset still needs datasource permission to render an otherwise
authorized dashboard after the remote-user session is established. This
bootstrap is idempotent and grants only ``datasource_access`` to the local
read role; it does not grant dashboard ownership, edit, SQL Lab, or admin
capabilities.
"""

import os

from sqlalchemy import text


def main() -> None:
    from superset.app import create_app

    app = create_app()
    from superset import db

    with app.app_context():
        from superset.connectors.sqla.models import SqlaTable

        security_manager = app.appbuilder.sm
        role = security_manager.find_role("Gamma")
        if role is None:
            raise RuntimeError("Superset role Gamma is missing")

        raw_ids = os.environ.get("SUPERSET_AUREVIA_DASHBOARD_IDS", "7")
        dashboard_ids = [int(value.strip()) for value in raw_ids.split(",") if value.strip()]
        if not dashboard_ids:
            raise RuntimeError("SUPERSET_AUREVIA_DASHBOARD_IDS must contain at least one id")

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

        db.session.commit()
        print(f"Configured datasource read access for Gamma ({len(tables)} tables)")


if __name__ == "__main__":
    main()
