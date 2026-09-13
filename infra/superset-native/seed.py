"""Deterministic demo dataset and published reports; nothing is downloaded from a DWH."""
import json
import os
from pathlib import Path
import sqlite3
import sys
from superset.app import create_app
from superset import db, security_manager

root = Path(os.environ["AUREVIA_SUPERSET_NATIVE_ROOT"])
zone = os.environ["AUREVIA_SUPERSET_ZONE"]
settings = json.loads((root / "settings.json").read_text())
app = create_app()
with app.app_context():
    admin = security_manager.find_user(username="administrator")
    if not admin:
        role = security_manager.find_role("Admin") or security_manager.add_role("Admin")
        admin = security_manager.add_user("administrator", "Aurevia", "Native BI Admin",
            f"native-{zone}@aurevia.demo.local", role, password=settings[zone]["adminPassword"])
    if sys.argv[1] == "admin":
        print(f"{zone}: bootstrap administrator ready; password stays in private settings")
        raise SystemExit(0)
    if zone != "operation":
        raise RuntimeError("Public Superset cannot contain analytical data or reports")
    with sqlite3.connect(root / "operation/analytics.db") as connection:
        connection.execute("CREATE TABLE IF NOT EXISTS department_sales (department TEXT PRIMARY KEY, amount INTEGER NOT NULL)")
        connection.executemany("INSERT INTO department_sales VALUES (?, ?) ON CONFLICT(department) DO UPDATE SET amount=excluded.amount",
            [("Sales", 250), ("HR", 180), ("Finance", 320), ("IT", 400)])
    from superset.models.core import Database
    from superset.connectors.sqla.models import SqlaTable
    from superset.models.slice import Slice
    from superset.models.dashboard import Dashboard
    database = db.session.query(Database).filter_by(database_name="Aurevia Native Demo Data").one_or_none()
    if not database:
        database = Database(database_name="Aurevia Native Demo Data",
                            sqlalchemy_uri="sqlite:///" + str(root / "operation/analytics.db"))
        db.session.add(database)
        db.session.flush()
    dataset = db.session.query(SqlaTable).filter_by(database_id=database.id, table_name="department_sales").one_or_none()
    if not dataset:
        dataset = SqlaTable(database=database, table_name="department_sales", owners=[admin])
        db.session.add(dataset)
        db.session.flush()
        dataset.fetch_metadata()
    metric = {"expressionType": "SIMPLE", "column": {"column_name": "amount", "type": "BIGINT"},
              "aggregate": "SUM", "label": "Total Sales", "hasCustomLabel": True}
    charts = []
    for title, kind, params in (
        ("Aurevia Department Sales", "table", {"query_mode": "raw", "all_columns": ["department", "amount"],
                                               "row_limit": 100, "include_time": False}),
        ("Aurevia Total Sales", "big_number_total", {"metric": metric, "subheader": "Demo total: 1150"}),
    ):
        chart = db.session.query(Slice).filter_by(slice_name=title, datasource_id=dataset.id).one_or_none()
        if not chart:
            chart = Slice(slice_name=title, viz_type=kind, datasource_type="table", datasource_id=dataset.id, owners=[admin])
            db.session.add(chart)
            db.session.flush()
        chart.params = json.dumps({"viz_type": kind, "datasource": f"{dataset.id}__table", "slice_id": chart.id, **params})
        charts.append(chart)
    dashboard = db.session.query(Dashboard).filter_by(slug="aurevia-native-demo").one_or_none()
    if not dashboard:
        dashboard = Dashboard(dashboard_title="Aurevia Native BI Demo", slug="aurevia-native-demo", owners=[admin])
        db.session.add(dashboard)
        db.session.flush()
    dashboard.published = True
    dashboard.slices = charts
    layout = {"DASHBOARD_VERSION_KEY": "v2",
              "ROOT_ID": {"id": "ROOT_ID", "type": "ROOT", "children": ["GRID_ID"]},
              "GRID_ID": {"id": "GRID_ID", "type": "GRID", "children": ["ROW-DEMO"], "parents": ["ROOT_ID"]},
              "HEADER_ID": {"id": "HEADER_ID", "type": "HEADER", "meta": {"text": "Aurevia Native BI Demo"}},
              "ROW-DEMO": {"id": "ROW-DEMO", "type": "ROW", "children": [], "parents": ["ROOT_ID", "GRID_ID"],
                           "meta": {"background": "BACKGROUND_TRANSPARENT"}}}
    for chart in charts:
        key = f"CHART-{chart.id}"
        layout["ROW-DEMO"]["children"].append(key)
        layout[key] = {"id": key, "type": "CHART", "children": [], "parents": ["ROOT_ID", "GRID_ID", "ROW-DEMO"],
                       "meta": {"chartId": chart.id, "sliceName": chart.slice_name, "width": 6, "height": 50}}
    dashboard.position_json = json.dumps(layout)
    dashboard.json_metadata = json.dumps({"native_filter_configuration": [], "color_scheme": "supersetColors",
        "color_scheme_domain": [], "label_colors": {}, "shared_label_colors": {}, "chart_configuration": {},
        "default_filters": "{}", "expanded_slices": {}, "refresh_frequency": 0, "cross_filters_enabled": False})
    gamma = security_manager.find_role("Gamma")
    # Demo viewers are read-only for dashboards, including automatic palette persistence.
    gamma.permissions = [permission for permission in gamma.permissions
        if not (permission.permission.name == "can_write" and permission.view_menu.name == "Dashboard")]
    permission = security_manager.add_permission_view_menu("datasource_access", dataset.get_perm())
    if permission not in gamma.permissions:
        gamma.permissions.append(permission)
    db.session.commit()
    evidence = {"zone": zone, "dashboardId": dashboard.id, "dashboardSlug": dashboard.slug,
                "datasetId": dataset.id, "chartIds": [chart.id for chart in charts],
                "expectedTotal": 1150, "rows": 4, "publicHasAnalyticalAssets": False}
    (root / "reports.json").write_text(json.dumps(evidence, indent=2) + "\n")
    print(json.dumps(evidence))
