import unittest
from types import SimpleNamespace
from bootstrap_permissions import grant_dashboard_role


class DashboardRoleTests(unittest.TestCase):
    def test_only_approved_dashboards_receive_viewer_role_and_existing_roles_survive(self):
        owner, viewer = object(), object()
        approved = SimpleNamespace(roles=[owner])
        unrelated = SimpleNamespace(roles=[owner])
        grant_dashboard_role([approved], viewer)
        self.assertEqual(approved.roles, [owner, viewer])
        self.assertEqual(unrelated.roles, [owner])

    def test_repeated_bootstrap_does_not_duplicate_role(self):
        viewer = object()
        dashboard = SimpleNamespace(roles=[viewer])
        grant_dashboard_role([dashboard], viewer)
        grant_dashboard_role([dashboard], viewer)
        self.assertEqual(dashboard.roles, [viewer])


if __name__ == '__main__':
    unittest.main()
