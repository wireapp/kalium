#!/usr/bin/env python3
"""Run Swift against real prepared stores on an isolated simulator, then remove it."""

import argparse
import base64
import json
import plistlib
from pathlib import Path
import shutil
import ssl
import subprocess
import tempfile
import threading
from urllib.parse import parse_qs, urlsplit
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

REPO = Path(__file__).resolve().parents[2]
NSE = REPO / "nse"
FRAMEWORK = NSE / "build/bin/iosSimulatorArm64/debugFramework"
FIXTURE = REPO / "domain/messaging/receiving/build/bin/iosSimulatorArm64/swiftFixtureDebugExecutable/swift-receiving-fixture.kexe"
APP_FIXTURE = REPO / "logic/build/bin/iosSimulatorArm64/swiftAppFixtureDebugExecutable/swift-app-verifier.kexe"
ENTITLEMENTS = REPO / "core/cryptography/src/iosTest/entitlements.plist"
REPORT = NSE / "build/reports/swift-receiving/result.json"


# CLI selection, native exporter and report path stay together.
SCENARIOS = {
    'calling-events': ('export-calling', 'calling-result.json',
        'Verify Proteus/MLS calling batches, Swift CallKit payload reporting and app reopen'),
    'multiple-payloads': ('export-multiple', 'multiple-result.json',
        'Receive two text payloads, including a quote and alert selection'),
    'mls-commit': ('export-mls-commit', 'mls-commit-result.json',
        'Receive an MLS commit notification before encrypted text'),
    'ordinary-events': ('export-ordinary', 'ordinary-result.json',
        'Receive metadata, text actions and ephemeral text; verify app timer handoff'),
    'synchronization': ('export-synchronization', 'synchronization-result.json',
        'Verify receipts, cross-device reads, profiles, preferences and safe configs'),
    'membership-events': ('export-membership', 'membership-result.json',
        'Verify membership and durable proposal handoff to the app scheduler'),
    'creation-events': ('export-creation', 'creation-result.json',
        'Verify new conversations, MLS welcomes and first text with app reopen'),
    'recovery-events': ('export-recovery', 'recovery-result.json',
        'Verify prepared MLS recovery welcomes and self rejoining with app reopen'),
    'reset-events': ('export-reset', 'reset-result.json',
        'Verify MLS reset/welcome ordering and app reopen with the replacement group'),
    'migration-events': ('export-migration', 'migration-result.json',
        'Verify Proteus to MIXED to MLS migration with app reopen'),
    'typing-events': ('export-typing', 'typing-result.json',
        'Verify ignored typing, preference ordering and app reopen'),
    'member-role-events': ('export-member-roles', 'member-role-result.json',
        'Verify role changes, promotion messages and missing-conversation fetch with app reopen'),
    'access-changes-events': ('export-access-changes', 'access-changes-result.json',
        'Verify access fields and apps-access system messages with app reopen'),
    'room-settings-events': ('export-room-settings', 'room-settings-result.json',
        'Verify channel permission and invitation-link updates with app reopen'),
    'remote-mute-events': ('export-remote-mute', 'remote-mute-result.json',
        'Verify ignored remote mute, ordered text and app reopen'),
    'x509-events': ('export-x509', 'x509-result.json',
        'Verify prepared X.509 receiving, CRLs and app identity reads'),
    'mls-cipher-events': ('export-mls-cipher', 'mls-cipher-result.json',
        'Verify installed-credential cipher changes and app reopen'),
    'mls-migration-events': ('export-mls-migration', 'mls-migration-result.json',
        'Verify migration settings, protocol refresh, resolution and app reopen'),
    'mls-protocol-events': ('export-mls-protocols', 'mls-protocol-result.json',
        'Verify self protocol upload, profile refresh, resolution and app reopen'),
    'mls-client-events': ('export-mls-clients', 'mls-client-result.json',
        'Verify changed team protocols, self-client refresh and app reopen'),
    'mls-settings-events': ('export-mls-settings', 'mls-settings-result.json',
        'Verify MLS settings and actual app reopen policy'),
    'e2ei-settings-events': ('export-e2ei-settings', 'e2ei-settings-result.json',
        'Verify noninteractive E2EI settings and the actual app observer'),
    'session-refresh-events': ('export-session-refresh', 'session-refresh-result.json',
        'Verify ordered token rotation, cache clearing and authenticated app reopen'),
    'self-conversation-events': ('export-self-conversations', 'self-conversation-result.json',
        'Verify self creation, welcome, own-device signal and registered app consumers'),
    'sent-connection-events': ('export-sent-connections', 'sent-connection-result.json',
        'Verify sent metadata, member placeholders and app reopen'),
    'accepted-connection-events': ('export-accepted-connections', 'accepted-connection-result.json',
        'Verify accepted resolution, consent warnings and migrated app history'),
    'connection-events': ('export-connections', 'connection-result.json',
        'Verify connection states, request decisions and app acknowledgement'),
    'federation-events': ('export-federation', 'federation-result.json',
        'Verify federation changes, request cleanup and app reads'),
    'meeting-conversation-events': ('export-meeting-conversations', 'meeting-conversation-result.json',
        'Verify meeting conversation creation, welcome and first text'),
    'meeting-events': ('export-meetings', 'meeting-result.json',
        'Verify meeting state, occurrences, deletion and app reads'),
    'identity-events': ('export-identity', 'identity-result.json',
        'Verify client/user/team state and the actual app new-client observer'),
    'conversation-cleanup-events': ('export-conversation-cleanup', 'conversation-cleanup-result.json',
        'Verify conversation clearing/deletion, real files and app reopen'),
    'cleanup-events': ('export-cleanup', 'cleanup-result.json',
        'Verify message file cleanup, shared files and missing cache recovery'),
    'mls-ignore-events': ('export-mls-ignores', 'mls-ignore-result.json',
        'Verify typed native own-commit and past-epoch ignores with app reopen'),
    'buffer-events': ('export-buffering', 'buffer-result.json',
        'Verify native MLS buffers and their actual app event consumer'),
    'decryption-events': ('export-decryption', 'decryption-result.json',
        'Verify failed-ciphertext rows, continuation and real app reads'),
    'preview-image-events': ('export-previews', 'preview-image-result.json',
        'Verify owned preview downloads and app image paths'),
    'preview-events': ('export-previews', 'preview-result.json',
        'Verify preview metadata and quotes with app image processing disabled'),
    'multipart-events': ('export-multipart', 'multipart-result.json',
        'Verify multipart metadata and the real app Cells download consumer'),
    'asset-events': ('export-assets', 'asset-result.json',
        'Verify asset metadata and the real app download/decrypt consumer'),
    'signal-events': ('export-signals', 'signal-result.json',
        'Verify client action, tracking IDs and ignored HistoryClient signals'),
    'content-events': ('export-content', 'content-result.json',
        'Verify composites, knock, unknown and location content with app reopen'),
    'one-on-one-events': ('export-one-on-one', 'one-on-one-result.json',
        'Verify active one-to-one selection and migrated history with app reopen'),
}


def run(*args, timeout=600, check=True):
    result = subprocess.run(args, cwd=REPO, text=True, capture_output=True, timeout=timeout)
    if check and result.returncode:
        raise RuntimeError(f"{Path(args[0]).name} failed:\n{result.stdout}\n{result.stderr}")
    return result.stdout


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--skip-build", action="store_true", help="Use already linked framework and fixture executables")
    scenarios = parser.add_mutually_exclusive_group()
    for flag, (_, _, help_text) in SCENARIOS.items():
        scenarios.add_argument(f"--{flag}", action="store_true", help=help_text)
    options = parser.parse_args()
    export_mode, report_name, _ = next(
        (scenario for flag, scenario in SCENARIOS.items() if getattr(options, flag.replace("-", "_"))),
        ("export", REPORT.name, None),
    )
    report = REPORT.with_name(report_name)
    mls_protocol_mode = options.mls_protocol_events or options.mls_migration_events
    mls_client_mode = options.mls_client_events or mls_protocol_mode
    mls_settings_mode = options.mls_settings_events or mls_client_mode or options.mls_cipher_events
    meeting_mode = options.meeting_events or options.meeting_conversation_events
    preview_mode = options.preview_events or options.preview_image_events
    asset_mode = options.asset_events or options.cleanup_events
    if not options.skip_build:
        print("Linking Swift framework and test fixture", flush=True)
        run("./gradlew", ":nse:linkDebugFrameworkIosSimulatorArm64",
            ":nse:generateAvsXcodeConfig",
            ":domain:messaging:receiving:linkSwiftFixtureDebugExecutableIosSimulatorArm64",
            ":logic:linkSwiftAppFixtureDebugExecutableIosSimulatorArm64", "--console=plain", "-q", timeout=1200)
    simulator = None
    server = None
    server_thread = None
    x509_server = None
    with tempfile.TemporaryDirectory(prefix="kalium-nse-swift-") as temporary:
        work = Path(temporary)
        try:
            avs_config = (NSE / "build/kalium-apple-avs-runtime/xcode/KaliumAvsRuntime.xcconfig").read_text()
            avs_root = Path(next(line.split(" = ", 1)[1] for line in avs_config.splitlines()
                                 if line.startswith("KALIUM_AVS_XCFRAMEWORK_DIR = ")))
            avs_info = plistlib.loads((avs_root / "Info.plist").read_bytes())
            avs_slice = next(item["LibraryIdentifier"] for item in avs_info["AvailableLibraries"]
                             if item.get("SupportedPlatformVariant") == "simulator" and "arm64" in item["SupportedArchitectures"])
            embedded_frameworks = work / "Frameworks"
            shutil.copytree(avs_root / avs_slice / "avs.framework", embedded_frameworks / "avs.framework")
            simulator = run("xcrun", "simctl", "create", "Kalium NSE isolated fixture",
                            "com.apple.CoreSimulator.SimDeviceType.iPhone-17-Pro",
                            "com.apple.CoreSimulator.SimRuntime.iOS-26-3").strip()
            print("Preparing isolated simulator", flush=True)
            run("xcrun", "simctl", "boot", simulator)
            run("xcrun", "simctl", "bootstatus", simulator, "-b", timeout=180)
            config = work / "tls.cnf"
            config.write_text("""[req]
distinguished_name=dn
x509_extensions=ext
prompt=no
[dn]
CN=Kalium NSE local fixture
[ext]
subjectAltName=DNS:localhost,IP:127.0.0.1
basicConstraints=critical,CA:TRUE
keyUsage=critical,digitalSignature,keyEncipherment,keyCertSign
extendedKeyUsage=serverAuth
""")
            certificate, key = work / "certificate.pem", work / "key.pem"
            run("openssl", "req", "-x509", "-newkey", "rsa:2048", "-nodes", "-keyout", str(key),
                "-out", str(certificate), "-days", "1", "-config", str(config))
            run("xcrun", "simctl", "keychain", simulator, "add-root-cert", str(certificate))
            if options.x509_events:
                from x509_fixture_server import X509FixtureServer
                x509_server = X509FixtureServer(certificate, key)
            exported = run("xcrun", "simctl", "spawn", simulator, str(FIXTURE), export_mode)
            manifest = next(line.removeprefix("NSE_SWIFT_MANIFEST=") for line in exported.splitlines()
                            if line.startswith("NSE_SWIFT_MANIFEST="))
            accounts = json.loads(Path(manifest).read_text())
            prepared_pki_requests = len(x509_server.requests) if x509_server else 0
            if options.preview_image_events:
                for account in accounts:
                    account["linkPreviewEnabled"] = True
                Path(manifest).write_text(json.dumps(accounts))
            if mls_client_mode:
                for account in accounts:
                    if account["route"].endswith("-read"):
                        # Match the retained capability so the second refresh is an exact SQL no-op.
                        account["selfClients"][0]["capabilities"] = []
                Path(manifest).write_text(json.dumps(accounts))
            requests = []
            self_client_requests = []
            protocol_uploads = []
            profile_requests = []
            connection_profile_requests = []
            metadata_requests = []
            asset_requests = []
            cell_requests = []
            token_requests = []
            refresh_requests = []
            refreshed_authorizations = []

            proposal_requests = []
            app_phase = False
            meeting_requests = []
            meeting_api_requests = []

            class Handler(BaseHTTPRequestHandler):
                def authorized(self, request_path):
                    expected = "Bearer access"
                    if options.session_refresh_events:
                        account = next((item for item in accounts if request_path.startswith(f"/{item['route']}/")), None)
                        if account is not None:
                            issued = sum(item[0] == account["route"] for item in refresh_requests)
                            if issued:
                                expected += f"-{issued}"
                    return self.headers.get("Authorization") == expected

                def do_PUT(self):
                    account = next((item for item in accounts if self.path == f"/{item['route']}/v8/self/supported-protocols"), None)
                    if not mls_protocol_mode or account is None or not self.authorized(self.path):
                        self.send_error(403)
                        return
                    request = json.loads(self.rfile.read(int(self.headers["Content-Length"])))
                    if set(request["supported_protocols"]) != {"proteus", "mls"}:
                        self.send_error(400)
                        return
                    protocol_uploads.append([account["route"], request["supported_protocols"]])
                    self.send_response(200)
                    self.send_header("Content-Length", "0")
                    self.end_headers()

                def do_GET(self):
                    if meeting_mode:
                        meeting_api_requests.append(["GET", self.path])
                    request_path = self.path.replace("/v17/", "/v8/").replace("/v16/", "/v8/") if meeting_mode else self.path
                    connection_account = next((item for item in accounts if request_path.startswith(f"/{item['route']}/v8/users/example.com/")), None)
                    if (options.connection_events or options.sent_connection_events or options.accepted_connection_events or options.session_refresh_events) and connection_account is not None:
                        if not self.authorized(request_path):
                            self.send_error(401)
                            return
                        peer = request_path.rsplit("/", 1)[-1]
                        body = json.dumps({
                            "qualified_id": {"id": peer, "domain": "example.com"}, "id": peer,
                            "name": "Peer " + peer.removeprefix("peer-"), "handle": "peer", "team": None,
                            "accent_id": 0, "assets": [], "deleted": False, "email": None, "expires_at": None,
                            "service": None, "supported_protocols": ["proteus", "mls"] if connection_account["mls"] else ["proteus"], "legalhold_status": "enabled",
                            "type": "regular", "app": None,
                        }).encode()
                        self.send_response(200)
                        self.send_header("Content-Type", "application/json")
                        self.send_header("Content-Length", str(len(body)))
                        self.end_headers()
                        self.wfile.write(body)
                        connection_profile_requests.append([connection_account["route"], peer])
                        if options.session_refresh_events:
                            refreshed_authorizations.append([connection_account["route"], peer, self.headers.get("Authorization")])
                        return
                    if options.multipart_events:
                        cell_account = next((item for item in accounts if request_path.startswith(f"/{item['route']}/cells/io/remote/")), None)
                        if cell_account is not None:
                            authorization = self.headers.get("Authorization", "")
                            if not authorization.startswith("AWS4-HMAC-SHA256 Credential=access/") or not self.headers.get("x-amz-date"):
                                self.send_error(403)
                                return
                            body = b"cell"
                            self.send_response(200)
                            self.send_header("Content-Type", "image/png")
                            self.send_header("Content-Length", str(len(body)))
                            self.end_headers()
                            self.wfile.write(body)
                            cell_requests.append([cell_account["route"], request_path.rsplit("/", 1)[-1]])
                            return
                    if not self.authorized(request_path):
                        self.send_error(401)
                        return
                    client_account = next((item for item in accounts if request_path == f"/{item['route']}/v8/clients"), None)
                    if mls_client_mode and client_account is not None:
                        body = json.dumps(client_account["selfClients"]).encode()
                        self.send_response(200)
                        self.send_header("Content-Type", "application/json")
                        self.send_header("Content-Length", str(len(body)))
                        self.end_headers()
                        self.wfile.write(body)
                        self_client_requests.append(client_account["route"])
                        return
                    if asset_mode or options.preview_image_events:
                        asset_account = next((item for item in accounts if request_path.startswith(f"/{item['route']}/v8/assets/example.com/")), None)
                        if asset_account is not None:
                            asset_id = request_path.rsplit("/", 1)[-1]
                            if self.headers.get("Asset-Token") != f"token-{asset_id}":
                                self.send_error(403)
                                return
                            body = base64.b64decode(asset_account["encryptedAsset"])
                            self.send_response(200)
                            self.send_header("Content-Type", "application/octet-stream")
                            self.send_header("Content-Length", str(len(body)))
                            self.end_headers()
                            self.wfile.write(body)
                            asset_requests.append([asset_account["route"], asset_id])
                            return
                    meeting_account = next((item for item in accounts if request_path.startswith(f"/{item['route']}/v8/meetings/example.com/")), None)
                    if meeting_mode and meeting_account is not None:
                        meeting_id = request_path.rsplit("/", 1)[-1]
                        meeting = dict(meeting_account["meetingMetadata"])
                        meeting["qualified_id"] = {"id": meeting_id, "domain": "example.com"}
                        assert meeting["tzid"] == "America/New_York" and "trial" not in meeting
                        body = json.dumps(meeting).encode()
                        self.send_response(200)
                        self.send_header("Content-Type", "application/json")
                        self.send_header("Content-Length", str(len(body)))
                        self.end_headers()
                        self.wfile.write(body)
                        meeting_requests.append([meeting_account["route"], meeting_id])
                        return
                    metadata_account = next((item for item in accounts if request_path == f"/{item['route']}/v8/conversations/example.com/conversation"), None)
                    if metadata_account is not None and (options.member_role_events or options.creation_events or options.membership_events or options.recovery_events or options.migration_events or options.one_on_one_events or options.meeting_conversation_events or options.sent_connection_events or options.self_conversation_events):
                        body = json.dumps(metadata_account["conversationMetadata"]).encode()
                        self.send_response(200)
                        self.send_header("Content-Type", "application/json")
                        self.send_header("Content-Length", str(len(body)))
                        self.end_headers()
                        self.wfile.write(body)
                        metadata_requests.append(metadata_account["route"])
                        return
                    account = next((item for item in accounts if request_path.startswith(f"/{item['route']}/v8/notifications?")), None)
                    if account is None:
                        self.send_error(404)
                        return
                    body = json.dumps(account["response"]).encode()
                    self.send_response(200)
                    self.send_header("Content-Type", "application/json")
                    self.send_header("Content-Length", str(len(body)))
                    self.end_headers()
                    self.wfile.write(body)
                    requests.append(account["route"])

                def do_POST(self):
                    if meeting_mode:
                        meeting_api_requests.append(["POST", self.path])
                    request_path = self.path.replace("/v17/", "/v8/").replace("/v16/", "/v8/") if meeting_mode else self.path
                    if options.session_refresh_events:
                        account = next((item for item in accounts if urlsplit(request_path).path == f"/{item['route']}/v8/access"), None)
                        if account is not None:
                            previous = sum(item[0] == account["route"] for item in refresh_requests)
                            old_cookie = "refresh" + (f"-{previous}" if previous else "")
                            if f"zuid={old_cookie}" not in self.headers.get("Cookie", "") or parse_qs(urlsplit(request_path).query).get("client_id") != [account["clientId"]]:
                                self.send_error(403)
                                return
                            index = previous + 1
                            body = json.dumps({"user": account["userValue"], "access_token": f"access-{index}",
                                               "expires_in": 3600, "token_type": "Bearer"}).encode()
                            refresh_requests.append([account["route"], old_cookie, index])
                            self.send_response(200)
                            self.send_header("Content-Type", "application/json")
                            self.send_header("Content-Length", str(len(body)))
                            self.send_header("Set-Cookie", f"zuid=refresh-{index}")
                            self.end_headers()
                            self.wfile.write(body)
                            return
                    if options.multipart_events:
                        token_account = next((item for item in accounts if request_path.split("?")[0] == f"/{item['route']}/v8/access"), None)
                        if token_account is not None:
                            if "zuid=refresh" not in self.headers.get("Cookie", ""):
                                self.send_error(403)
                                return
                            body = json.dumps({"user": token_account["userValue"], "access_token": "access",
                                               "expires_in": 3600, "token_type": "Bearer"}).encode()
                            self.send_response(200)
                            self.send_header("Content-Type", "application/json")
                            self.send_header("Content-Length", str(len(body)))
                            self.end_headers()
                            self.wfile.write(body)
                            token_requests.append(token_account["route"])
                            return
                    if not self.authorized(request_path):
                        self.send_error(401)
                        return
                    profile_account = next((item for item in accounts if request_path == f"/{item['route']}/v8/list-users"), None)
                    if profile_account is not None and (options.membership_events or options.creation_events or options.one_on_one_events or meeting_mode or options.accepted_connection_events or mls_protocol_mode):
                        request = json.loads(self.rfile.read(int(self.headers["Content-Length"])))
                        found = [{
                            "qualified_id": user, "id": user["id"], "name": "Third user", "handle": "third",
                            "team": None, "accent_id": 0, "assets": [], "deleted": False, "email": None,
                            "expires_at": None, "service": None, "supported_protocols": ["proteus", "mls"],
                            "legalhold_status": "enabled", "type": "regular", "app": None,
                        } for user in request["qualified_ids"]]
                        if (options.one_on_one_events and profile_account["route"] == "proteus") or (options.accepted_connection_events and not profile_account["mls"]):
                            for user in found:
                                user["supported_protocols"] = ["proteus"]
                        if mls_protocol_mode:
                            for user in found:
                                user["name"] = "Protocol peer"
                                user["supported_protocols"] = ["proteus", "mls"] if profile_account["mls"] else ["proteus"]
                        body = json.dumps({"found": found, "failed": []}).encode()
                        self.send_response(200)
                        self.send_header("Content-Type", "application/json")
                        self.send_header("Content-Length", str(len(body)))
                        self.end_headers()
                        self.wfile.write(body)
                        profile_requests.append(profile_account["route"])
                        return
                    account = next((item for item in accounts if request_path == f"/{item['route']}/v8/mls/commit-bundles"), None)
                    if account is None or not account.get("pendingProposal"):
                        self.send_error(404)
                        return
                    proposal_requests.append([account["route"], app_phase])
                    commit = self.rfile.read(int(self.headers["Content-Length"]))
                    (Path(account["root"]) / "app-proposal-commit").write_bytes(commit)
                    body = json.dumps({"time": "2024-01-01T00:00:00Z", "events": account.get("proposalReturnedEvents", [])}).encode()
                    self.send_response(201)
                    self.send_header("Content-Type", "application/json")
                    self.send_header("Content-Length", str(len(body)))
                    self.end_headers()
                    self.wfile.write(body)

                def log_message(self, *_):
                    pass

            server = ThreadingHTTPServer(("127.0.0.1", 18443), Handler)
            tls = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
            tls.load_cert_chain(certificate, key)
            server.socket = tls.wrap_socket(server.socket, server_side=True)
            server_thread = threading.Thread(target=server.serve_forever, daemon=True)
            server_thread.start()
            sdk = run("xcrun", "--sdk", "iphonesimulator", "--show-sdk-path").strip()
            consumer = work / "receive"
            run("xcrun", "--sdk", "iphonesimulator", "swiftc", "-parse-as-library", "-application-extension", "-sdk", sdk,
                "-target", "arm64-apple-ios15.0-simulator", "-F", str(FRAMEWORK), "-framework", "KaliumNSE",
                "-F", str(embedded_frameworks), "-framework", "avs",
                "-Xlinker", "-rpath", "-Xlinker", "@executable_path/Frameworks",
                "-Xlinker", "-rpath", "-Xlinker", str(FRAMEWORK),
                "-Xlinker", "-sectcreate", "-Xlinker", "__TEXT", "-Xlinker", "__entitlements", "-Xlinker", str(ENTITLEMENTS),
                str(NSE / "src/swiftTest/Receive.swift"), str(NSE / "host/KaliumNSECallKitReporter.swift"), "-o", str(consumer))
            print("Running real Swift Proteus and MLS receiving", flush=True)
            output = run("xcrun", "simctl", "spawn", simulator, str(consumer), manifest, timeout=90)
            assert "SWIFT_RECEIVE_PASSED Proteus" in output and "SWIFT_RECEIVE_PASSED MLS" in output
            assert requests == [account["route"] for account in accounts], requests
            run("xcrun", "simctl", "spawn", simulator, str(FIXTURE), "verify")
            if x509_server:
                assert len(x509_server.requests) == prepared_pki_requests, "Receiving unexpectedly fetched PKI data"
            assert proposal_requests == [], "NSE sent an optional proposal commit"
            app_phase = True
            print("Opening the real app session after Swift receiving", flush=True)
            app_output = run("xcrun", "simctl", "spawn", simulator, str(APP_FIXTURE), manifest)
            assert "APP_AFTER_SWIFT_PASSED Proteus" in app_output and "APP_AFTER_SWIFT_PASSED MLS" in app_output
            if options.membership_events:
                assert "APP_PROPOSAL_SCHEDULER_PASSED" in app_output
                assert "APP_PROPOSAL_RESPONSE_CONSUMED" in app_output
                assert proposal_requests == [[account["route"], True] for account in accounts if account["pendingProposal"]]
                assert set(profile_requests) == {account["route"] for account in accounts}
                run("xcrun", "simctl", "spawn", simulator, str(FIXTURE), "after-app-proposal")
                after = run("xcrun", "simctl", "spawn", simulator, str(APP_FIXTURE), manifest, "after-proposal")
                assert "APP_AFTER_SWIFT_PASSED MLS" in after
            if options.creation_events:
                assert set(profile_requests) == {account["route"] for account in accounts}
                assert metadata_requests == [account["route"] for account in accounts if account["fetchedCreation"]]
                assert "APP_CREATION_STATE_PASSED" in app_output
                assert "APP_MIXED_PROTOCOLS_PASSED" in app_output
            if options.recovery_events:
                assert "APP_RECOVERY_STATE_PASSED" in app_output
                assert metadata_requests == [account["route"] for account in accounts if account["route"] == "mls-read"]
            if options.reset_events:
                assert "APP_RESET_STATE_PASSED" in app_output
                assert metadata_requests == []
            REPORT.parent.mkdir(parents=True, exist_ok=True)
            if options.migration_events:
                assert app_output.count("APP_MIGRATION_STATE_PASSED") == 2
                assert sorted(metadata_requests) == ["mls", "mls", "mls-read", "mls-read"]
            if options.one_on_one_events:
                assert app_output.count("APP_ONE_ON_ONE_STATE_PASSED") == 2
                assert set(profile_requests) == {"proteus", "mls"}
                assert metadata_requests == ["mls"]
            if options.content_events:
                assert app_output.count("APP_CONTENT_STATE_PASSED") == 2
            if options.typing_events:
                assert app_output.count("APP_TRANSIENT_QUEUE_PASSED") == 2
                assert app_output.count("APP_TYPING_POLICY_PASSED") == 2
                assert metadata_requests == [] and profile_requests == []
            if options.member_role_events:
                assert app_output.count("APP_MEMBER_ROLE_STATE_PASSED") == 4
                assert metadata_requests == [account["route"] for account in accounts if account["memberRoleFetched"]]
                assert profile_requests == []
            if options.access_changes_events:
                assert app_output.count("APP_ACCESS_CHANGES_STATE_PASSED") == 4
                assert metadata_requests == [] and profile_requests == []
            if options.room_settings_events:
                assert app_output.count("APP_ROOM_SETTINGS_STATE_PASSED") == 4
                assert metadata_requests == [] and profile_requests == []
            if options.remote_mute_events:
                assert app_output.count("APP_REMOTE_MUTE_STATE_PASSED") == 2
                assert metadata_requests == [] and profile_requests == []
            if options.signal_events:
                assert app_output.count("APP_SIGNAL_STATE_PASSED") == 4
            if asset_mode:
                assert app_output.count("APP_ASSET_STATE_PASSED") == 2
                assert sorted(asset_requests) == sorted([[account["route"], asset_id] for account in accounts
                                                       for asset_id in ("asset", "image", "audio", "video")])
            if options.multipart_events:
                assert app_output.count("APP_MULTIPART_DOWNLOAD_PASSED") == 2
                assert cell_requests == [[account["route"], cell_id + ".png"] for account in accounts
                                         for cell_id in ("cell-two", "cell-one")], cell_requests
                assert token_requests == [account["route"] for account in accounts for _ in range(2)], token_requests
            if preview_mode:
                assert app_output.count("APP_PREVIEW_METADATA_PASSED") == 2
            if options.preview_image_events:
                assert len(asset_requests) == 2, asset_requests
                assert app_output.count("APP_PREVIEW_IMAGE_PASSED") == 2
            if options.decryption_events:
                assert app_output.count("APP_DECRYPTION_FAILURE_PASSED") == 2
            if options.buffer_events:
                assert "APP_NATIVE_BUFFER_CONSUMED" in app_output
            if options.meeting_events:
                assert app_output.count("APP_MEETING_STATE_PASSED") == 2
                for account in accounts:
                    assert [item[1] for item in meeting_requests if item[0] == account["route"]] == [
                        "meeting", "meeting", "meeting", "removed-meeting"]
                    assert account["route"] in profile_requests
            if meeting_mode:
                assert meeting_api_requests and all("/v17/" in path for _, path in meeting_api_requests), meeting_api_requests
            if options.meeting_conversation_events:
                assert app_output.count("APP_MEETING_STATE_PASSED") == 3
                assert app_output.count("APP_CREATION_STATE_PASSED") == 3
                assert len(accounts) == 3
                for account in accounts:
                    assert [item[1] for item in meeting_requests if item[0] == account["route"]] == ["meeting"]
                    assert account["route"] in profile_requests
                assert metadata_requests == [item["route"] for item in accounts if item["meetingConversationWelcomeFirst"]]
            if options.federation_events:
                assert app_output.count("APP_FEDERATION_STATE_PASSED") == 4
            if options.identity_events:
                assert app_output.count("APP_IDENTITY_STATE_PASSED") == 4
                assert app_output.count("APP_NEW_CLIENT_CONSUMED") == 4
            if options.conversation_cleanup_events:
                assert app_output.count("APP_CONVERSATION_CLEANUP_PASSED") == 4
            if mls_settings_mode:
                assert app_output.count("APP_MLS_SETTINGS_CONSUMED") == 4
                assert app_output.count("APP_DISABLED_MLS_POLICY_PASSED") == 1
                assert app_output.count("APP_DISABLED_MLS_READINESS_REVOKED") == 1
                assert metadata_requests == []
                assert profile_requests == ([item["route"] for item in accounts] if mls_protocol_mode else [])
            if x509_server:
                assert app_output.count("APP_X509_PKI_AND_CRL_CONSUMED") == 2
                assert app_output.count("APP_X509_SELF_REVOCATION_CONSUMED") == 1
                assert [r["path"] for r in x509_server.requests[prepared_pki_requests:]] == ["/crl", "/crl"], x509_server.requests[prepared_pki_requests:]
                assert all(r["authorization"] is None for r in x509_server.requests)
            if options.mls_cipher_events:
                assert app_output.count("APP_MLS_CHANGED_CIPHER_USABLE") == 1
            if mls_client_mode:
                assert app_output.count("APP_MLS_CLIENT_REFRESH_CONSUMED") == 4
                assert self_client_requests == [item["route"] for item in accounts for _ in range(2)]
            if options.mls_migration_events:
                assert app_output.count("APP_MLS_MIGRATION_SETTINGS_CONSUMED") == 4
            if mls_protocol_mode:
                assert app_output.count("APP_MLS_PROTOCOL_RESOLUTION_CONSUMED") == 4
                assert [item[0] for item in protocol_uploads] == [item["route"] for item in accounts]
            if options.e2ei_settings_events:
                assert app_output.count("APP_E2EI_SETTINGS_CONSUMED") == 4
                assert app_output.count("APP_E2EI_CREATE_REQUIRED") == 1
            if options.session_refresh_events:
                assert app_output.count("APP_REFRESHED_SESSION_REQUEST_PASSED") == 2
                assert len(refresh_requests) == 4
                for account in accounts:
                    route = account["route"]
                    assert [item[1:] for item in refresh_requests if item[0] == route] == [["refresh", 1], ["refresh-1", 2]]
                    assert [item[1:] for item in refreshed_authorizations if item[0] == route] == [
                        ["peer-refresh-one", "Bearer access-1"], ["peer-refresh-two", "Bearer access-2"], ["app-refreshed", "Bearer access-2"]]
            if options.self_conversation_events:
                assert app_output.count("APP_SELF_CONVERSATION_PASSED") == 3
                assert len(accounts) == 3
                assert metadata_requests == [account["route"] for account in accounts if account["selfConversationWelcomeFirst"]]
                assert profile_requests == []
            if options.sent_connection_events:
                assert app_output.count("APP_PENDING_CONNECTION_CREATION_PASSED") == 2
                assert app_output.count("APP_SENT_CONNECTION_PASSED") == 2
                assert metadata_requests == [account["route"] for account in accounts]
                assert profile_requests == []
                assert len(connection_profile_requests) == 2
            if options.accepted_connection_events:
                assert app_output.count("APP_ACCEPTED_CONNECTION_PASSED") == 4
                assert metadata_requests == []
                assert profile_requests == [account["route"] for account in accounts]
                assert len(connection_profile_requests) == 4
            if options.connection_events:
                assert app_output.count("APP_CONNECTION_NOTIFICATION_PASSED") == 2
                assert app_output.count("APP_CONNECTION_ACKNOWLEDGEMENT_PASSED") == 2
                for account in accounts:
                    assert [peer for route, peer in connection_profile_requests if route == account["route"]] == [
                        "peer-request", "peer-blocked", "peer-ignored", "peer-consent", "peer-delete-target", "peer-delete-target", "peer-request"]
            if options.cleanup_events:
                assert app_output.count("APP_FILE_CLEANUP_STATE_PASSED") == 2
                assert app_output.count("APP_EXPIRATION_FILE_CLEANUP_PASSED") == 2
            report.write_text(json.dumps({
                "success": True, "authenticatedREST": requests, "assetDownloads": asset_requests,
                "conversationCleanupAndAppReopen": options.conversation_cleanup_events,
                "meetingRequests": meeting_requests,
                "meetingApiRequests": meeting_api_requests,
                "connectionStateAndAppAcknowledgement": options.connection_events,
                "sentConnectionMetadataAndAppReads": options.sent_connection_events,
                "pendingConnectionCreationAndAppReads": options.sent_connection_events,
                "sessionRefreshRequests": refresh_requests,
                "refreshedAuthorizations": refreshed_authorizations,
                "selfConversationCreationWelcomeAndAppConsumer": options.self_conversation_events,
                "mlsSettingsAndAppPolicy": mls_settings_mode,
                "proposalCommitRequests": proposal_requests,
                "proposalTimerAndAppResponseConsumer": options.membership_events,
                "typingPoliciesAndAppReopen": options.typing_events,
                "transientBacklogAndCanonicalCursor": options.typing_events,
                "memberRolesAndAppReopen": options.member_role_events,
                "accessChangesAndAppReopen": options.access_changes_events,
                "roomSettingsAndAppReopen": options.room_settings_events,
                "ignoredRemoteMuteAndAppReopen": options.remote_mute_events,
                "preparedX509AndAppCrlCheck": options.x509_events,
                "pkiRequests": x509_server.requests if x509_server else [],
                "mlsCipherChangeAndAppReopen": options.mls_cipher_events,
                "mlsSelfClientRefresh": mls_client_mode,
                "mlsProtocolUploadAndResolution": mls_protocol_mode,
                "mlsMigrationSettingsAndAppReopen": options.mls_migration_events,
                "selfProtocolUploads": protocol_uploads,
                "selfClientRequests": self_client_requests,
                "e2eiSettingsAndAppObserver": options.e2ei_settings_events,
                "acceptedConnectionResolutionAndAppHistory": options.accepted_connection_events,
                "connectionProfileRequests": connection_profile_requests,
                "identityStateAndAppConsumer": options.identity_events,
                "federationStateAndAppReads": options.federation_events,
                "meetingConversationCreationWelcomeAndAppReads": options.meeting_conversation_events,
                "typedMLSIgnoresAndAppReopen": options.mls_ignore_events,
                "nativeBuffersAndAppConsumer": options.buffer_events,
                "decryptionFailureAndAppRead": options.decryption_events, "previewMetadataAndAppRead": preview_mode, "ownedPreviewImageDownloadsAndAppRead": options.preview_image_events,
                "multipartAppDownloads": cell_requests, "cellsTokenRefreshes": token_requests,
                "messageFileCleanupAndMissingCacheRecovery": options.cleanup_events,
                "appExpirationFileCleanupFailureAndRetry": options.cleanup_events,
                "swiftConstructionAndReceiving": True, "processedTargetDoesNotAlertAgain": True,
                "storedMessageAndDeliveryVerified": True, "nextCiphertextAfterReopenVerified": ["Proteus", "MLS"],
                "appSessionMessageAndNextCiphertextVerified": ["Proteus", "MLS"],
                "payloadsPerNotification": [len(account["response"]["notifications"][-1]["payload"]) for account in accounts],
                "membershipAndProposalAppSchedulerHandoff": options.membership_events,
                "authenticatedMembershipProfiles": profile_requests,
                "newConversationWelcomeAndFirstText": options.creation_events,
                "preparedRecoveryWelcomeAndAppReopen": options.recovery_events,
                "backendResetAndAppReopen": options.reset_events,
                "protocolMigrationAndAppReopen": options.migration_events,
                "oneOnOneHistoryAndActiveConversationAfterAppReopen": options.one_on_one_events,
                "regularContentAndGenericNotificationWithAppReopen": options.content_events,
                "mixedProtocolsWithinNotificationAndAppReopen": options.creation_events,
                "authenticatedConversationMetadata": metadata_requests,
                "synchronizationAndReadState": options.synchronization,
                "ordinaryMetadataActionsAndAppExpirationHandoff": options.ordinary_events,
                "runtime": "iOS-26-3", "temporarySimulator": simulator,
                "mlsCommitBeforeText": options.mls_commit,
            }, indent=2) + "\n")
            print("Swift receiving and app handoff verification passed for Proteus and MLS", flush=True)
        finally:
            if server_thread is not None:
                server.shutdown()
            if x509_server is not None:
                x509_server.close()
            if server is not None:
                server.server_close()
            if server_thread is not None:
                server_thread.join(timeout=5)
            if simulator is not None:
                run("xcrun", "simctl", "shutdown", simulator, check=False)
                run("xcrun", "simctl", "delete", simulator)


if __name__ == "__main__":
    main()
