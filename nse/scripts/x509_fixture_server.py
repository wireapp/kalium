"""Local test CA and ACME responses used only to prepare native X.509 fixtures."""

import base64
from datetime import datetime, timedelta, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
import ssl
import threading
import time
from urllib.parse import parse_qs, urlsplit

from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.x509.oid import ExtensionOID, NameOID

BASE = "https://localhost:18444"


class X509FixtureServer:
    def __init__(self, certificate, key):
        self.requests = []
        self.serial = 1
        self.nonce = 0
        self.issued = []
        self.revoked = []
        self.crl_mode = "valid"
        self.order = {}
        self.leaf = b""
        self.ca_key = ec.generate_private_key(ec.SECP256R1())
        subject = x509.Name([x509.NameAttribute(NameOID.COMMON_NAME, "NSE fixture root")])
        now = datetime.now(timezone.utc)
        self.root = (x509.CertificateBuilder().subject_name(subject).issuer_name(subject)
                     .public_key(self.ca_key.public_key()).serial_number(1)
                     .not_valid_before(now - timedelta(days=1)).not_valid_after(now + timedelta(days=365))
                     .add_extension(x509.BasicConstraints(ca=True, path_length=None), critical=True)
                     .add_extension(x509.KeyUsage(False, False, False, False, False, True, True, None, None), critical=True)
                     .add_extension(x509.SubjectKeyIdentifier.from_public_key(self.ca_key.public_key()), critical=False)
                     .add_extension(x509.AuthorityKeyIdentifier.from_issuer_public_key(self.ca_key.public_key()), critical=False)
                     .add_extension(x509.CRLDistributionPoints([x509.DistributionPoint(
                         full_name=[x509.UniformResourceIdentifier(f"{BASE}/crl")], relative_name=None,
                         reasons=None, crl_issuer=None)]), critical=False)
                     .sign(self.ca_key, hashes.SHA256()))
        owner = self

        class Handler(BaseHTTPRequestHandler):
            def log_message(self, *_):
                pass

            def do_HEAD(self):
                self.respond()

            def do_GET(self):
                path = urlsplit(self.path).path
                owner.requests.append({"method": "GET", "path": path, "authorization": self.headers.get("Authorization")})
                if path == "/roots":
                    self.respond(owner.root.public_bytes(serialization.Encoding.PEM))
                elif path == "/directory":
                    self.respond({"newNonce": f"{BASE}/nonce", "newAccount": f"{BASE}/account",
                                  "newOrder": f"{BASE}/order", "revokeCert": f"{BASE}/revoke"})
                elif path == "/control":
                    control = parse_qs(urlsplit(self.path).query)
                    if "reset" in control:
                        owner.issued = []
                        owner.revoked = []
                        owner.crl_mode = "valid"
                    if "mode" in control:
                        owner.crl_mode = control["mode"][0]
                    if "revoke" in control:
                        owner.revoked = (list(owner.issued) if control["revoke"][0] == "all" else
                                         [owner.issued[-1 if control["revoke"][0] == "peer" else 0]])
                    self.respond()
                elif path == "/crl" or path.startswith("/proxy/"):
                    if owner.crl_mode == "failure":
                        self.respond(b"CRL unavailable", status=503)
                    else:
                        if owner.crl_mode == "slow":
                            time.sleep(5)
                        self.respond(owner.crl())
                else:
                    self.respond(b"Unknown fixture endpoint", status=404)

            def do_POST(self):
                path = urlsplit(self.path).path
                owner.requests.append({"method": "POST", "path": path, "authorization": self.headers.get("Authorization")})
                body = self.rfile.read(int(self.headers.get("Content-Length", "0")))
                if path == "/account":
                    self.respond({"status": "valid", "orders": f"{BASE}/orders"})
                elif path == "/order":
                    owner.order = self.payload(body)
                    self.respond(owner.order_response("pending"))
                elif path.startswith("/auth/"):
                    device = path.endswith("device")
                    kind = "wireapp-device" if device else "wireapp-user"
                    identifier = next(i for i in owner.order["identifiers"] if i["type"] == kind)
                    self.respond({"status": "pending", "expires": "2999-01-01T00:00:00Z",
                                  "identifier": identifier, "challenges": [owner.challenge(device, "pending")]})
                elif path.startswith("/challenge/"):
                    self.respond(owner.challenge(path.endswith("device"), "valid"))
                elif path == "/finalize":
                    csr = base64.urlsafe_b64decode(self.payload(body)["csr"] + "===")
                    owner.leaf = owner.issue(csr)
                    self.respond(owner.order_response("valid", f"{BASE}/certificate"))
                elif path == "/certificate":
                    self.respond(owner.leaf)
                else:
                    self.respond(b"Unknown fixture endpoint", status=404)

            @staticmethod
            def payload(body):
                return json.loads(base64.urlsafe_b64decode(json.loads(body)["payload"] + "==="))

            def respond(self, body=b"", status=200):
                if isinstance(body, dict):
                    body = json.dumps(body).encode()
                self.send_response(status)
                owner.nonce += 1
                self.send_header("replay-nonce", f"nonce-{owner.nonce}")
                self.send_header("Content-Length", str(len(body)))
                self.end_headers()
                if self.command != "HEAD":
                    try:
                        self.wfile.write(body)
                    except (BrokenPipeError, ConnectionResetError, ssl.SSLError):
                        pass  # Deliberate cancellation tests close the connection.

        self.server = ThreadingHTTPServer(("127.0.0.1", 18444), Handler)
        context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
        context.load_cert_chain(certificate, key)
        self.server.socket = context.wrap_socket(self.server.socket, server_side=True)
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()

    def close(self):
        self.server.shutdown()
        self.server.server_close()
        self.thread.join()

    def order_response(self, status, certificate=None):
        result = {"status": status, "expires": "2999-01-01T00:00:00Z", "finalize": f"{BASE}/finalize",
                  "identifiers": self.order["identifiers"],
                  "authorizations": [f"{BASE}/auth/device", f"{BASE}/auth/user"]}
        result.update({k: self.order[k] for k in ("notBefore", "notAfter") if k in self.order})
        if certificate:
            result["certificate"] = certificate
        return result

    @staticmethod
    def challenge(device, status):
        return {"type": "wire-dpop-01" if device else "wire-oidc-01",
                "url": f"{BASE}/challenge/{'device' if device else 'user'}", "status": status,
                "token": "MDEyMzQ1Njc4OWFiY2RlZg",
                "target": "https://wire.test/clients/abc123/access-token" if device else "https://idp.test/authorize"}

    def issue(self, csr_der):
        csr = x509.load_der_x509_csr(csr_der)
        identity = json.loads(next(i["value"] for i in self.order["identifiers"] if i["type"] == "wireapp-user"))
        subject = x509.Name([x509.NameAttribute(NameOID.COMMON_NAME, identity["name"]),
                             x509.NameAttribute(NameOID.ORGANIZATION_NAME, identity["domain"])])
        self.serial += 1
        self.issued.append(self.serial)
        now = datetime.now(timezone.utc)
        builder = (x509.CertificateBuilder().subject_name(subject).issuer_name(self.root.subject)
                   .public_key(csr.public_key()).serial_number(self.serial)
                   .not_valid_before(now - timedelta(minutes=1)).not_valid_after(now + timedelta(days=1))
                   .add_extension(x509.BasicConstraints(ca=False, path_length=None), critical=True)
                   .add_extension(x509.KeyUsage(True, False, False, False, False, False, False, None, None), critical=True)
                   .add_extension(x509.SubjectKeyIdentifier.from_public_key(csr.public_key()), critical=False)
                   .add_extension(x509.AuthorityKeyIdentifier.from_issuer_public_key(self.ca_key.public_key()), critical=False)
                   .add_extension(x509.CRLDistributionPoints([x509.DistributionPoint(
                       full_name=[x509.UniformResourceIdentifier(f"{BASE}/crl")], relative_name=None,
                       reasons=None, crl_issuer=None)]), critical=False))
        generated = {ExtensionOID.BASIC_CONSTRAINTS, ExtensionOID.KEY_USAGE, ExtensionOID.SUBJECT_KEY_IDENTIFIER,
                     ExtensionOID.AUTHORITY_KEY_IDENTIFIER, ExtensionOID.CRL_DISTRIBUTION_POINTS}
        for extension in csr.extensions:
            if extension.oid not in generated:
                builder = builder.add_extension(extension.value, extension.critical)
        return builder.sign(self.ca_key, hashes.SHA256()).public_bytes(serialization.Encoding.PEM)

    def crl(self):
        now = datetime.now(timezone.utc)
        builder = (x509.CertificateRevocationListBuilder().issuer_name(self.root.subject)
                   .last_update(now - timedelta(minutes=1)).next_update(now + timedelta(days=1))
                   .add_extension(x509.AuthorityKeyIdentifier.from_issuer_public_key(self.ca_key.public_key()), critical=False))
        for serial in self.revoked:
            builder = builder.add_revoked_certificate(x509.RevokedCertificateBuilder().serial_number(serial)
                                                     .revocation_date(now - timedelta(minutes=1)).build())
        return builder.sign(self.ca_key, hashes.SHA256()).public_bytes(serialization.Encoding.DER)
