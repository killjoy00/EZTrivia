import json
import unittest
import status

class StatusTests(unittest.TestCase):
    def setUp(self):
        self.registry = json.loads(status.REGISTRY.read_text())
    def test_inventory(self):
        apps = status.validate_registry(self.registry)
        self.assertEqual(len(apps), 10)
        by_id = {a["id"]: a for a in apps}
        self.assertEqual(by_id["starport"]["source"], by_id["stuffy-land"]["source"])
        self.assertNotEqual(by_id["starport"]["ios"]["bundle"], by_id["stuffy-land"]["ios"]["bundle"])
    def test_run_success_does_not_infer_release(self):
        def fetch(url):
            if "/actions/" in url:
                return {"workflow_runs":[{"id":123,"conclusion":"success","head_sha":"a"*40}]}
            if "/commits/" in url:
                return {"sha":"b"*40}
            return None
        report = status.render(self.registry, fetch, "2026-11-26 00:00")
        self.assertIn("[success]", report)
        self.assertIn("Not verified | Not verified | Not verified | Not verified", report)
        self.assertNotIn("a"*40, report)
    def test_exact_apple_identity(self):
        route = {"bundle":"com.example.app"}
        self.assertEqual(status.apple_public(route, lambda _: {"results":[{"bundleId":"com.example.other","version":"1.0"}]}), status.UNKNOWN)
        self.assertEqual(status.apple_public(route, lambda _: {"results":[{"bundleId":"com.example.app","version":"1.2.3"}]}), "1.2.3 (US listing)")
        self.assertEqual(status.apple_public(route, lambda _: {"results":[]}), status.UNKNOWN)
    def test_unavailable(self):
        report = status.render(self.registry, lambda _: None, "2026-11-26 00:00")
        self.assertIn("TurnPro", report)
        self.assertIn("Route not verified", report)
        self.assertNotIn("[success]", report)
    def test_malformed_evidence(self):
        route = {"repo":"killjoy00/EZTrivia","workflow":"testflight.yml"}
        self.assertEqual(status.workflow_run(route, lambda _: {"workflow_runs":[{"id":"bad","conclusion":"success"}]}), status.UNKNOWN)
        self.assertEqual(status.head("killjoy00/EZTrivia", lambda _: {"sha":"bad"}), status.UNKNOWN)

if __name__ == "__main__":
    unittest.main()
