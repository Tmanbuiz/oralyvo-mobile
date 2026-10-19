import copy
import datetime as dt
import hashlib
import pathlib
import plistlib
import sys
import tempfile
import unittest

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1]))
from ios_signing import select_profile, write_configuration


class SigningSelectionTests(unittest.TestCase):
    def setUp(self):
        self.cert = b"fixture-certificate"
        self.fingerprint = hashlib.sha1(self.cert).hexdigest().upper()
        self.profile = {
            "UUID": "12345678-1234-1234-1234-123456789ABC", "TeamIdentifier": ["ABCDE12345"],
            "Platform": ["iOS"], "ExpirationDate": dt.datetime(2030, 1, 1),
            "DeveloperCertificates": [self.cert],
            "Entitlements": {"application-identifier": "ABCDE12345.app.oralyvo.mobile", "get-task-allow": False},
        }
        self.now = dt.datetime(2026, 10, 19, tzinfo=dt.timezone.utc)

    def choose(self, profiles, identities=None):
        return select_profile(profiles, {self.fingerprint} if identities is None else identities, "app.oralyvo.mobile", self.now)

    def test_selects_profile_with_available_private_key_and_latest_expiry(self):
        older = copy.deepcopy(self.profile)
        older["ExpirationDate"] = dt.datetime(2028, 1, 1)
        selected, identity = self.choose([older, self.profile])
        self.assertIs(selected, self.profile)
        self.assertEqual(identity, self.fingerprint)

    def test_rejects_wrong_bundle_expired_development_and_enterprise_profiles(self):
        for field, value in [("ExpirationDate", dt.datetime(2025, 1, 1)), ("ProvisionedDevices", ["device"]), ("ProvisionsAllDevices", True)]:
            candidate = copy.deepcopy(self.profile)
            candidate[field] = value
            with self.assertRaises(ValueError):
                self.choose([candidate])
        candidate = copy.deepcopy(self.profile)
        candidate["Entitlements"]["application-identifier"] = "ABCDE12345.other.app.oralyvo.mobile"
        with self.assertRaises(ValueError):
            self.choose([candidate])

    def test_rejects_profile_without_imported_private_key(self):
        with self.assertRaisesRegex(ValueError, "private key"):
            self.choose([self.profile], identities=set())

    def test_archive_and_export_use_the_same_profile_team_and_certificate(self):
        with tempfile.TemporaryDirectory() as directory:
            output = pathlib.Path(directory)
            write_configuration(self.profile, self.fingerprint, "app.oralyvo.mobile", output)
            config = (output / "signing.xcconfig").read_text()
            options = plistlib.loads((output / "export_options.plist").read_bytes())
            self.assertIn("PROVISIONING_PROFILE_SPEC = " + self.profile["UUID"], config)
            self.assertEqual(options["provisioningProfiles"]["app.oralyvo.mobile"], self.profile["UUID"])
            self.assertIn(options["teamID"], config)
            self.assertIn(options["signingCertificate"], config)


if __name__ == "__main__":
    unittest.main()
