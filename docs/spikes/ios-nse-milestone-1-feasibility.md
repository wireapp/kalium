# iOS NSE lightweight Kalium — Milestone 1 feasibility report

Date: 2026-07-13

Status: **Completed for host/simulator spike — physical iOS/NSE gates deferred**

This report records the repository-side and simulator evidence gathered for ADR 0010. It is a
disposable spike, not a production NSE implementation. No automated tests were added or run.

## Executive result

The repository can build and load a dedicated KMP framework that links CoreCrypto, Apple
persistence types, Ktor's Darwin engine, the notification API, and optionally the existing
notification AVS module. Caller-supplied paths and non-blocking `flock` work in host probes, and a
minimal Swift simulator app loaded the framework successfully.

The repository-side Milestone 1 spike passes for host and simulator evidence. A combined macOS
binary reproducibly crashes in CoreCrypto's SQLCipher/OpenSSL key derivation when the current
notification AVS artifact is linked. Archive and
DWARF inspection now proves that this macOS image combines ABI-incompatible OpenSSL and BoringSSL
implementations. This result must not be projected directly onto iOS: the current iOS CoreCrypto
archive uses SQLCipher's CommonCrypto provider and has no overlapping defined symbol with the iOS
AVS archive in the audited simulator slice.

A split dynamic-framework host experiment passed on macOS in both framework link orders. One
framework owned CoreCrypto/network/persistence and the other owned notification AVS; a Swift host
ran the AVS native lifecycle before and after sequential CoreCrypto opens in the same process. This
is a viable isolation direction, not a production go. By explicit spike-sequencing decision,
receive-only extraction may proceed using the iOS Simulator while physical-device gates remain
open. No physical iPhone is attached, and the
required App Group, shared Keychain, locked-device, APNs, backend WebSocket, decryption, memory,
extension-validation, and deadline gates remain untested.

Recommendation: **provisional split packaging and proceed with receive-only extraction on the iOS
Simulator**, not production go and not vendor-blocked. Keep AVS outside the CoreCrypto KMP image if
the current artifact is retained,
or preferably obtain a genuinely notification-only AVS artifact with hidden/namespaced crypto
dependencies. The Apple encrypted handoff store and shared Keychain configuration are also
mandatory prerequisites. Do not cut over any cursor/state or claim production readiness based on
the host and simulator evidence below.

## Spike harness

The disposable module is `:sample:nse-feasibility`. It intentionally has no dependency on
`:logic`, sending, full calling, backup, cells, or the main user-session assembly.

It provides:

- `KaliumNseFeasibility.framework` for `iosArm64`, `iosSimulatorArm64`, and `macosArm64`;
- a macOS probe executable;
- caller-supplied shared-root creation and byte round-trip;
- an exclusive non-blocking POSIX `flock` held by an open file descriptor;
- sequential CoreCrypto open/close at a caller-supplied path;
- Darwin HTTP engine and `NotificationApi.consumeLiveEvents` linkage;
- current Apple persistence/Keychain capability reporting; and
- optional notification AVS linkage with `-PnseFeasibility.includeAvs=true`.

AVS is disabled by default. This is deliberate: it makes the CoreCrypto baseline executable, while
the opt-in variant preserves the failing combination for reproduction and binary inspection.

## Environment

| Item | Evidence |
| --- | --- |
| Host | Apple Silicon `arm64`, Darwin 25.5.0 |
| Xcode | 26.3, build 17C529 |
| JDK | OpenJDK 21.0.10 |
| Simulator | iPhone 16 Pro, iOS 18.4, identifier `52A30086-E67D-4A6A-90D8-A121334D902C` |
| Physical device | **Unavailable**; `xcdevice` discovery found no attached iPhone |
| Signing/entitlements | No repository Xcode host/NSE project or entitlement files |
| Backend credentials/push | Not supplied; no real notification WebSocket or APNs run |

## Evidence matrix

| Gate | Result | Scope and evidence |
| --- | --- | --- |
| Dedicated framework shape | Supplemental pass | Debug dynamic frameworks linked for simulator and device `arm64`; device artifact is unsigned and unrun. |
| Framework load | Simulator pass | A minimal Swift iOS 18.4 simulator app imported the framework and executed `probeSharedRoot`; console printed `frameworkLoaded=true`, `passed=true`. This was an app process, not an NSE target. |
| Caller-supplied shared root | Host + simulator pass | macOS round-trip: 11.959 ms. Simulator resolved the caller-provided sandbox temporary root and created `kalium-nse/v1/probe-account`. No App Group entitlement was available. |
| Non-blocking process lock | Host pass | Initial acquire 0.548 ms; contended acquire returned `Resource temporarily unavailable` in 0.671 ms; after `SIGKILL` of the owner, reacquire succeeded in 0.545 ms. Not proven between an iOS app and NSE. |
| CoreCrypto sequential open/close without AVS | Host pass | Same path opened and closed twice in 29.155 ms using the AVS-disabled binary. No Proteus/MLS application message was decrypted. |
| CoreCrypto + current notification AVS, one macOS image | **Fail** | AVS-enabled binary exits 139 in SQLCipher/OpenSSL key derivation; exact mixed-provider binding is detailed below. This is not an iOS runtime result. |
| Split CoreCrypto and notification-AVS dynamic frameworks | Host pass | A strongly linked Swift host ran AVS/CoreCrypto/AVS and CoreCrypto/AVS/CoreCrypto with reversed framework link order; both runs exited 0. |
| iOS AVS/CoreCrypto archive collision audit | Supplemental pass | The audited simulator archives have zero overlapping global defined symbols; iOS CoreCrypto uses CommonCrypto rather than embedded OpenSSL. A signed runtime probe is still required. |
| Notification WebSocket linkage | Link-only pass | Darwin engine constructed/closed and `NotificationApi.consumeLiveEvents` referenced in 8.089 ms. No authenticated connection, marker, staging, or ACK was exercised. |
| Notification AVS lifecycle | Host partial pass | Factory creation, empty-event validation, and balanced `close()` completed in 0.265 ms. No captured call payload or callback was exercised. |
| Notification-only AVS binary | **Fail/redesign** | Same full AVS archive is used. Simulator Mach-O grew by 11,578,784 bytes (+26.3%) and added broad media frameworks. |
| Shared Keychain | **Blocked** | Current config exposes only `serviceName`; access group and accessibility are unconfigurable. No entitlements or locked-device run. |
| Encrypted handoff DB | **Fail/current implementation** | Apple SQLDelight uses native SQLite with an encryption TODO and ignores database passphrases. No decrypted blob may be stored there for production. |
| Physical NSE lifetime/memory | **Blocked** | No attached device or signed NSE host. Cold start, RSS, jetsam, deadline margin, and locked-device behavior are unknown. |
| App/NSE CoreCrypto handoff | **Blocked** | Sequential host open/close is not cross-process iOS state handoff. Existing-handle behavior is unknown. |
| Real Proteus/MLS receive | **Blocked** | No account state or captured encrypted payloads. MLS handshake, buffered message, Welcome/defer behavior, and passive state persistence remain unproven. |

## Critical macOS AVS/CoreCrypto collision

The AVS-disabled host executable produced:

```text
gate=corecrypto-sequential-open-close passed=true elapsedNanos=29155000
```

After rebuilding the same module with `-PnseFeasibility.includeAvs=true`, the AVS factory/close
probe passed, but the unchanged CoreCrypto probe exited with status 139 and no Kotlin exception.
LLDB recorded `EXC_BAD_ACCESS` at address `0x8` on the CoreCrypto blocking worker:

```text
EVP_DigestInit_ex
HMAC_Init_ex
kdf_pbkdf2_derive
ossl_pkcs5_pbkdf2_hmac_ex
PKCS5_PBKDF2_HMAC
sqlcipher_openssl_kdf
sqlcipher_cipher_ctx_key_derive
sqlcipher_codec_key_derive
sqlite3Codec
...
rusqlite::Connection::execute_batch
```

The archive and final-image audit establishes the exact macOS failure mechanism:

- AVS 10.4.9's `libavsobjc.a` embeds BoringSSL in objects including `bcm.o` and `pbkdf.o`.
- CoreCrypto KMP 9.3.3.4's macOS `libcore_crypto_ffi.a` embeds OpenSSL 3.5.5 and SQLCipher's OpenSSL
  provider.
- The two archives have 2,068 overlapping global defined symbols. Both define
  `EVP_DigestInit_ex`, `HMAC_Init_ex`, and `PKCS5_PBKDF2_HMAC`.
- The final executable selects CoreCrypto/OpenSSL `EVP_DigestInit_ex` and PBKDF2, but selects
  AVS/BoringSSL `HMAC_Init_ex` and its HMAC context functions.
- OpenSSL's `kdf_pbkdf2_derive` directly branches to that selected `HMAC_Init_ex`. DWARF maps the
  function to `third_party/boringssl/src/crypto/fipsmodule/hmac/hmac.c.inc:131`.

SQLCipher/OpenSSL therefore passes OpenSSL structures into BoringSSL code with a different ABI,
which explains the invalid access. Archive order or allow-multiple-definition flags can merely
select a different incompatible mixture and are not an acceptable fix.

### iOS target distinction

The current iOS simulator and device CoreCrypto archives use SQLCipher's CommonCrypto provider.
The simulator archive contains `sqlcipher_cc_kdf`, has an undefined reference to Apple's
`CCKeyDerivationPBKDF`, and does not define the audited OpenSSL `EVP`, `HMAC`, or PBKDF2 entry
points. The iOS simulator AVS and CoreCrypto archives have zero overlapping global defined symbols.
The corresponding EVP/HMAC/PBKDF2 symbols in the combined iOS framework come from AVS BoringSSL.

Consequently, the macOS exit 139 is a valid host-packaging defect but is not evidence that the same
collision occurs on iOS with these target-specific artifacts. Successful iOS linking is still not
runtime proof; signed physical-device execution and real CoreCrypto receive operations remain open.

## Split dynamic-framework isolation experiment

The disposable `:sample:nse-feasibility-avs-isolation` module builds
`KaliumNseAvsIsolation.framework` with `:domain:calling-notifications` and no CoreCrypto, network, or
persistence dependency. The existing AVS-disabled `KaliumNseFeasibility.framework` owns
CoreCrypto/network/persistence and has no AVS dependency. Both are explicitly dynamic frameworks.

`run-split-framework-probe.sh` compiles a small Swift host twice with reversed framework link order.
It exchanges no Kotlin objects between frameworks. Each host strongly links both images and runs:

```text
AVS create/start/process/end/close -> CoreCrypto open/close twice -> AVS again
CoreCrypto open/close twice -> AVS create/start/process/end/close -> CoreCrypto again
```

The AVS probe passes a non-empty deliberately invalid payload. Native `process` returns code 94,
which proves that validation did not short-circuit before `start/process/end`; this is not a valid
call-payload behavior test. Both host orders exited 0 and reported
`gate=split-framework-coexistence passed=true`.

Mach-O evidence:

- both framework images are `MH_DYLIB` with `TWOLEVEL` and distinct `@rpath` install names;
- both Swift host variants list both frameworks as separate load dylibs, in reversed order;
- the Core framework contains its OpenSSL/SQLCipher symbols;
- the AVS framework's `EVP_DigestInit_ex`, `HMAC_Init_ex`, and `PKCS5_PBKDF2_HMAC` are
  non-external/private in that image; and
- neither host has undefined crypto or `wcall` references.

This demonstrates process-level coexistence for the bounded macOS probe. It does not prove iOS
extension loading, App Store acceptance, a real call-event callback, two-Kotlin-runtime memory cost,
or physical-device CoreCrypto safety. If this packaging is retained, Swift must orchestrate the two
frameworks using Swift-native scalars/bytes; Kotlin objects must not cross their runtime boundary.

## Binary linkage evidence

Simulator debug frameworks:

| Variant | Mach-O bytes | Framework disk blocks (`du -sk`) | `wcall` symbols |
| --- | ---: | ---: | ---: |
| AVS disabled | 44,065,336 | 43,064 | 0 |
| AVS enabled | 55,644,120 | 54,368 | 65 |
| Delta | +11,578,784 (+26.3%) | +11,304 | +65 |

The AVS-enabled Mach-O additionally links AudioToolbox, AVFoundation, CFNetwork, CoreAudio,
CoreGraphics, CoreMedia, CoreVideo, Metal, MetalKit, MobileCoreServices, Network, QuartzCore,
ReplayKit, SystemConfiguration, VideoToolbox, and CoreServices. Its strings contain 314 matches for
`wcall_`, `flowmgr_`, `mediamgr_`, or `ecall_` even though the exported Kotlin surface calls only
notification-event APIs.

The cached `avs-kmp` simulator cinterop KLIB is 113,838,796 bytes and embeds a 427,710,280-byte
uncompressed `libavsobjc.a`; the device archive is similarly approximately 427 MB uncompressed.
The simulator and device framework links emitted extensive missing/duplicate debug-symbol warnings
from AVS objects. The link returned success, but the warnings and host crash require owner review.

The AVS-enabled iOS device debug framework linked as an `arm64` dynamic library with a 55,088,768
byte Mach-O and minimum OS 14.0. It was not signed, embedded, launched, or checked by App Store
validation.

These are debug-framework measurements, not final release/install-size budgets. A release Mach-O,
linker map, containing `.appex` delta, on-device RSS, and `APPLICATION_EXTENSION_API_ONLY=YES` build
remain required.

## Existing Apple storage and lifecycle gaps

### Keychain

`ApplePersistenceConfig` exposes only a service name and `KeychainSettings` is constructed with that
value alone. The dependency can accept custom Keychain properties, but the repository does not yet
set an access group, accessibility class, or synchronizable policy. The app and NSE therefore
cannot currently be asserted to read the same authentication and database keys.

The device spike must validate both read directions, locked after first unlock, before first unlock,
and supported reinstall combinations. First-use key generation must happen under the process lock.

### SQLDelight application databases

The Apple driver is plain native SQLite. `PlatformDatabaseData.kt` has a SQLCipher TODO, and the
Apple user/global assembly passes no usable database passphrase. This makes the existing user DB
unacceptable as a decrypted protobuf handoff store. WAL is neither encryption nor a cross-process
lock.

### CoreCrypto lifecycle

`coreCryptoCentral` places its encrypted keystore under the caller-provided root. A Proteus or MLS
client can close the underlying CoreCrypto client, but `CoreCryptoCentral` itself exposes no close
operation if wrapper creation fails. Underlying CoreCrypto transactions use `NonCancellable`, so
deadline safety cannot be inferred from coroutine cancellation and needs physical-device timing.

Apple `migrateDatabaseKey` is also unimplemented. Existing accounts using legacy key aliases may
fail before any NSE receive operation.

### Paths

Kalium accepts caller root strings and does not resolve an App Group container. That is an
appropriate host boundary, but the signed native host must pass the same canonical App Group URL to
both processes. Existing sandbox CoreCrypto state also needs a one-time locked, crash-recoverable
migration before the NSE may use the new root.

## Exact verification commands

No test or full-build task was run.

```bash
./gradlew :sample:nse-feasibility:compileKotlinMacosArm64 \
  -PUSE_UNIFIED_CORE_CRYPTO=true

./gradlew :sample:nse-feasibility:linkDebugExecutableMacosArm64 \
  -PUSE_UNIFIED_CORE_CRYPTO=true \
  -PnseFeasibility.includeAvs=false

sample/nse-feasibility/run-macos-probes.sh \
  /tmp/kalium-nse-feasibility-m1-no-avs

./gradlew :sample:nse-feasibility:linkDebugExecutableMacosArm64 \
  -PUSE_UNIFIED_CORE_CRYPTO=true \
  -PnseFeasibility.includeAvs=true

sample/nse-feasibility/build/bin/macosArm64/debugExecutable/kalium-nse-feasibility.kexe \
  avs /tmp/kalium-nse-feasibility-m1-with-avs

sample/nse-feasibility/build/bin/macosArm64/debugExecutable/kalium-nse-feasibility.kexe \
  corecrypto /tmp/kalium-nse-feasibility-m1-with-avs

./gradlew :sample:nse-feasibility:linkDebugFrameworkIosSimulatorArm64 \
  -PUSE_UNIFIED_CORE_CRYPTO=true \
  -PnseFeasibility.includeAvs=false

./gradlew :sample:nse-feasibility:linkDebugFrameworkIosSimulatorArm64 \
  -PUSE_UNIFIED_CORE_CRYPTO=true \
  -PnseFeasibility.includeAvs=true

./gradlew :sample:nse-feasibility:linkDebugFrameworkIosArm64 \
  -PUSE_UNIFIED_CORE_CRYPTO=true \
  -PnseFeasibility.includeAvs=true

./gradlew \
  :sample:nse-feasibility:linkDebugFrameworkMacosArm64 \
  :sample:nse-feasibility-avs-isolation:linkDebugFrameworkMacosArm64 \
  -PUSE_UNIFIED_CORE_CRYPTO=true \
  -PnseFeasibility.includeAvs=false

sample/nse-feasibility/run-split-framework-probe.sh \
  /tmp/kalium-nse-split-framework

xcrun otool -hv \
  sample/nse-feasibility/build/bin/macosArm64/debugFramework/\
KaliumNseFeasibility.framework/KaliumNseFeasibility

xcrun otool -hv \
  sample/nse-feasibility-avs-isolation/build/bin/macosArm64/debugFramework/\
KaliumNseAvsIsolation.framework/KaliumNseAvsIsolation
```

Binary inspection used `file`, `du -sk`, `stat`, `otool -D`, `otool -L`, `otool -l`, `size -m`,
`nm`, `dwarfdump`, and `strings`. Archive symbol intersections counted only global definitions with
`nm -gU`, not undefined references or archive diagnostics. The original simulator load used a
temporary Swift app embedding the AVS-enabled dynamic framework; it was not added to the
repository. The split-framework Swift source and repeatable compilation script are retained in the
disposable sample harness.

## Required device/backend gates

The following remain open and must not be inferred from simulator or macOS evidence:

- signed NSE invocation and guaranteed completion-handler/fallback behavior;
- matching App Group and Keychain Sharing entitlements;
- locked-device and post-reboot file/Keychain availability;
- app/NSE two-process lock contention and forced-termination release;
- sequential access to real shared CoreCrypto state while the other process has existed or been
  suspended;
- Proteus application-message decryption;
- MLS application, proposal/commit, handshake-only, buffered-message, and Welcome/defer behavior;
- an authenticated consumable notification WebSocket reaching its marker;
- durable event staging before transport ACK and redelivery behavior;
- real notification call payload processing and callbacks;
- APNs `mutable-content` delivery;
- cold-start time, peak RSS, binary/install delta, transaction bounds, and deadline margin; and
- extension-safe API/App Store validation.

## Decision

**Complete the repository-side milestone, provisionally adopt split packaging, and carry the
physical-device gates forward.** The macOS
failure is diagnosed and the split dynamic frameworks coexist in the bounded host probe. The
current iOS archives do not contain that same symbol collision, so the work is not presently
vendor-blocked. Nevertheless, do not merge AVS and CoreCrypto into one KMP image based on linker
order, and do not treat this host result as production approval.

Milestone 2 may now extract shared receive-only functionality and use simulator compile/link/load
evidence. Before a production go decision, the split arrangement must load and run in a signed physical-device NSE,
pass extension-safe/App Store validation, process a real call payload, and meet binary/RSS/deadline
budgets. A namespaced or hidden, genuinely notification-only AVS artifact remains preferred because
the current AVS framework still packages broad full-AVS code. Encrypted Apple handoff storage and
shared Keychain configuration also remain mandatory. Until those gates pass, both frameworks are
disposable feasibility artifacts only.
