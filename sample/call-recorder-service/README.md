# JVM Wire call-recorder service

This experimental headless sample registers and owns one Kalium service runtime for a dedicated Wire
account. It receives and checkpoints authenticated events, decodes/decrypts calling signalling,
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

## Local test configuration

This sample intentionally uses one plain JSON file for fast local testing. It contains the Wire
email/password, backend links, conversation targets, generated state/crypto database passwords,
and the registered client ID. Do not use this credential layout for deployment or commit the
filled file.

Copy the template and fill the account and backend fields:

```bash
cp sample/call-recorder-service/call-recorder-config.example.json \
  sample/call-recorder-service/call-recorder-config.json
```

```json
{
  "email": "recorder@example.com",
  "password": "replace-me",
  "backendDomain": "example.com",
  "apiUrl": "https://api.example.com",
  "webSocketUrl": "https://api.example.com",
  "selfConversations": "auto"
}
```

Keep the other fields from the template. On the first run, the sample:

1. Logs in with the email and password.
2. Generates local state, Proteus, and MLS database passwords.
3. Generates Proteus prekeys and registers a permanent recorder client.
4. For an MLS target, uses the returned client ID to initialize MLS, publish its public key, and upload key packages.
5. Writes the discovered backend API version, `userId`, `userDomain`, `clientId`, and generated database passwords back to the JSON.

The service chooses notification delivery from the registered client capability. When consumable
notifications are unavailable, it incrementally fetches pending legacy notifications from its
durable checkpoint before continuing on the legacy live WebSocket.

Later runs log in again and restore that client and its crypto databases. The account password and
access/refresh tokens are never logged; tokens remain in the runtime's encrypted state files. The
JSON still contains enough secrets to unlock those files, which is acceptable only for this test.

`selfConversations` defaults to `auto`, which discovers the account's self-conversations from the
backend and caches them in the running service. For an explicit override, entries are comma-separated. Proteus entries use
`qualified-conversation-id|proteus`. MLS entries use
`qualified-conversation-id|mls|group-id|optional-epoch`.

The optional fields in the template cover 2FA (`verificationCode`), API version, alternate backend
links, client label/model, state directories, MLS ciphersuite, team ID, and AVS settings. E2EI
backends requiring certificate enrollment are not supported by this quick provisioning path.

## Run

Build the native AVS libraries into `./native/libs`, then run with the JSON file:

```bash
make
./gradlew :sample:call-recorder-service:jvmRun \
  -Pkalium.providerCacheScope=LOCAL \
  --args="--recordings-dir ./recordings"
```

Other options are `--shutdown-timeout-seconds` and `--notification-open-timeout-seconds`. Send
`SIGTERM`/`SIGINT` for graceful shutdown. Lifecycle logs contain no user, client, conversation,
token, group, or calling-message identifiers.

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
