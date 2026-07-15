# JVM Wire call-recorder service

This experimental headless sample owns one Kalium service runtime for one pre-provisioned Wire
identity. It receives and checkpoints authenticated events, decodes/decrypts calling signalling,
forwards signalling to AVS, automatically answers eligible ringing calls, maintains Wire/SFT/MLS
calling transport, and records remote playout to a separate WAV file for each sequential call.

## Audio path and limits

The sample uses the real AVS `wcall_audio_record` API through Kalium's
`ServiceCalling.recordAudio`. AVS replaces the normal audio device with its process-global record
device and writes remote playout as raw 16 kHz, mono, signed 16-bit PCM. The sample selects a unique
`.pcm.partial` path before answering and converts it to an inspectable WAV after AVS reports a
terminal call state. Normal shutdown closes AVS first and then finalizes any remaining recording.

This AVS API is not a PCM callback, microphone input, local participant capture, or bidirectional
PSTN/SIP media bridge. It captures the mixed remote playout heard by the headless participant. It
also cannot isolate concurrent calls, so this service enforces one active call per process. Separate
sequential calls are supported. If WAV finalization fails, the raw `.pcm.partial` file is retained
for recovery instead of being silently deleted.

## Required configuration

Use a dedicated, already registered Wire user/client. The current Kalium service composition
restores session and crypto identity state; it does not perform email/password login, register a
client, publish Proteus prekeys, or discover self-conversation IDs. On the first launch of a
pre-provisioned state directory, `WIRE_ACCESS_TOKEN` and `WIRE_REFRESH_TOKEN` seed the encrypted
session store. Later launches read the stored, refreshable session and do not require token
variables.

Required identity/backend variables:

```text
WIRE_USER_ID
WIRE_USER_DOMAIN
WIRE_CLIENT_ID
WIRE_BACKEND_DOMAIN                 # optional; defaults to WIRE_USER_DOMAIN
WIRE_API_URL
WIRE_WEBSOCKET_URL
WIRE_API_VERSION                    # optional; defaults to 12
WIRE_FEDERATION                     # optional; defaults to true
WIRE_ON_PREMISES                    # optional; defaults to true
```

Required encrypted state variables:

```text
WIRE_STATE_KEY_BASE64
WIRE_PROTEUS_PASSPHRASE_BASE64
WIRE_MLS_PASSPHRASE_BASE64
WIRE_STATE_DIR                      # optional; defaults to ./call-recorder-state
WIRE_CRYPTO_DIR                     # optional; defaults to ./call-recorder-crypto
WIRE_CRYPTO_MODE                    # RESTORE_EXISTING (default) or CREATE_NEW
```

The decoded values must be non-empty. Keep keys outside the state/recording directories and inject
them through the deployment secret manager. Never place them in shell history, process arguments,
logs, or source control.

Required self-conversation targets are comma-separated. Each entry uses `qualified-id|protocol`:

```text
WIRE_SELF_CONVERSATIONS='opaque-id@example.com|proteus'
WIRE_SELF_CONVERSATIONS='opaque-id@example.com|mls|opaque-group-id|0'
```

The MLS epoch is optional. Multiple restored Proteus/MLS self-conversation targets may be listed.
The group ID is the CoreCrypto MLS group identifier expected by the service runtime.

Session variables are required only when the encrypted state store has no session:

```text
WIRE_ACCESS_TOKEN
WIRE_REFRESH_TOKEN
WIRE_TOKEN_TYPE                     # optional; defaults to Bearer
WIRE_COOKIE_LABEL                   # optional
```

Optional backend link variables (`WIRE_ACCOUNTS_URL`, `WIRE_BLACKLIST_URL`, `WIRE_TEAMS_URL`,
`WIRE_WEBSITE_URL`) default to `WIRE_API_URL`. `WIRE_USER_AGENT`, `WIRE_TEAM_ID`,
`WIRE_AUDIO_CBR`, `WIRE_MLS_CIPHERSUITE`, and `WIRE_AVS_READY_TIMEOUT_SECONDS` are also supported.
Certificate pinning and authenticated API proxies are intentionally not configurable in this first
sample. E2EI conversations that return CRL distribution points fail closed because the sample has
no deployment-specific CRL verifier.

## Run

Build the native AVS libraries into `./native/libs` first, then explicitly select local provider
caches and run:

```bash
./gradlew :sample:call-recorder-service:jvmRun \
  -Pkalium.providerCacheScope=LOCAL \
  --args="--recordings-dir ./recordings"
```

Other safe command-line options are `--shutdown-timeout-seconds` and
`--notification-open-timeout-seconds`. Credentials are deliberately not accepted as arguments.
Send `SIGTERM`/`SIGINT` for graceful shutdown. Lifecycle logs contain no user, client,
conversation, token, group, or calling-message identifiers.

## Recording files and operations

Final names have the form `call_<UTC timestamp>_<random opaque id>.wav`. Names do not contain phone
numbers, user names, tokens, or conversation IDs. Files are created without overwrite. The default
directory is `./recordings`.

Deployment owners are responsible for recording consent and notification, legal basis, retention,
access control, encryption at rest, backup policy, auditability, and secure deletion. Recordings
and partial PCM files contain sensitive communications and must be protected accordingly.

The service APIs remain experimental pending calling-team confirmation and the deferred test suite
listed in `docs/operations/kalium-headless-calling-service.md`. Do not treat this sample as a
production deployment approval.
