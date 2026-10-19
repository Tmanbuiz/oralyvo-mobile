"""Select an installed App Store profile whose certificate has a private key.

Writes build-local Xcode signing settings and export options. Never prints the
profile, private keys, certificates, or environment credentials.
"""
import argparse
import datetime as dt
import hashlib
import pathlib
import plistlib
import re
import subprocess


def select_profile(profiles, identities, bundle_id, now=None):
    now = now or dt.datetime.now(dt.timezone.utc)
    candidates = []
    for profile in profiles:
        entitlements = profile.get("Entitlements", {})
        identifier = entitlements.get("application-identifier", "")
        expiry = profile.get("ExpirationDate")
        if not isinstance(expiry, dt.datetime):
            continue
        if expiry.tzinfo is None:
            expiry = expiry.replace(tzinfo=dt.timezone.utc)
        if expiry <= now or identifier.partition(".")[2] != bundle_id:
            continue
        if profile.get("ProvisionedDevices") is not None or profile.get("ProvisionsAllDevices"):
            continue
        if entitlements.get("get-task-allow", False) or "iOS" not in profile.get("Platform", []):
            continue
        teams = profile.get("TeamIdentifier", [])
        uuid = profile.get("UUID", "")
        if not teams or not re.fullmatch(r"[A-Za-z0-9]{10}", teams[0]):
            continue
        if not re.fullmatch(r"[0-9A-Fa-f-]{36}", uuid):
            continue
        for certificate in profile.get("DeveloperCertificates", []):
            fingerprint = hashlib.sha1(certificate).hexdigest().upper()
            if fingerprint in identities:
                candidates.append((expiry, profile, fingerprint))
    if not candidates:
        raise ValueError(
            f"No unexpired App Store profile for {bundle_id} matches an installed signing identity. "
            "Check the fetch-signing-files and keychain add-certificates steps. "
            "The provisioning profile must include a certificate whose private key is in the build keychain."
        )
    _, profile, fingerprint = max(candidates, key=lambda item: item[0])
    return profile, fingerprint


def write_configuration(profile, fingerprint, bundle_id, output):
    output.mkdir(parents=True, exist_ok=True)
    team = profile["TeamIdentifier"][0]
    uuid = profile["UUID"]
    (output / "signing.xcconfig").write_text(
        "CODE_SIGN_STYLE = Manual\n"
        f"DEVELOPMENT_TEAM = {team}\n"
        f"PROVISIONING_PROFILE = {uuid}\n"
        f"PROVISIONING_PROFILE_SPEC = {uuid}\n"
        f"CODE_SIGN_IDENTITY = {fingerprint}\n"
        "CODE_SIGNING_ALLOWED = YES\n"
    )
    with (output / "export_options.plist").open("wb") as handle:
        plistlib.dump({
            "method": "app-store-connect",
            "teamID": team,
            "signingStyle": "manual",
            "signingCertificate": fingerprint,
            "provisioningProfiles": {bundle_id: uuid},
            "manageAppVersionAndBuildNumber": False,
            "uploadSymbols": True,
        }, handle)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--bundle-id", required=True)
    parser.add_argument("--output", type=pathlib.Path, default=pathlib.Path("build/signing"))
    args = parser.parse_args()
    result = subprocess.run(["security", "find-identity", "-v", "-p", "codesigning"], check=True, capture_output=True, text=True)
    identities = set(re.findall(r"\b([A-Fa-f0-9]{40})\b", result.stdout.upper()))
    home = pathlib.Path.home()
    locations = [home / "Library/MobileDevice/Provisioning Profiles", home / "Library/Developer/Xcode/UserData/Provisioning Profiles"]
    profiles = []
    for location in locations:
        for path in location.glob("*.mobileprovision"):
            decoded = subprocess.run(["security", "cms", "-D", "-i", str(path)], capture_output=True)
            if decoded.returncode == 0:
                try:
                    profiles.append(plistlib.loads(decoded.stdout))
                except (ValueError, plistlib.InvalidFileException):
                    continue
    try:
        profile, fingerprint = select_profile(profiles, identities, args.bundle_id)
    except ValueError as error:
        parser.exit(1, str(error) + "\n")
    write_configuration(profile, fingerprint, args.bundle_id, args.output)
    print(f"Matching App Store signing configuration prepared for {args.bundle_id}.")


if __name__ == "__main__":
    main()
