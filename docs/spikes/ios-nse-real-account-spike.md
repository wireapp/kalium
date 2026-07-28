# iOS NSE real-account integration spike

Date: 2026-07-18

Status: fail-closed implementation under audit; not production-ready.

## What this path does

`RealNotificationExtension` is the temporary real-account entry point in the
`KaliumNotificationExtension` framework. The caller supplies the canonical APNs account UUID and
an absolute deadline. Kalium then:

1. acquires a non-blocking account-wide process lock before opening account state;
2. opens the same App Group-backed Kalium root used by the logged-in app;
3. reads auth state through the same Keychain service and access group;
4. resolves the UUID to the one authoritative qualified local session and its registered client ID;
5. acquires the non-blocking account/client process lock;
6. opens the encrypted App Group handoff store after both process locks are held;
7. opens the authenticated consumable-notification WebSocket with a new synchronization marker;
8. performs one bounded catch-up and closes at the matching marker;
9. stages the raw event before accepting its delivery tag locally;
10. applies real Proteus or MLS receive bytes to the existing CoreCrypto state and stages the
   complete child batch;
11. resolves Proteus external content and decodes the exact `GenericMessage` protobuf;
12. converts decrypted calling content into the existing notification-only AVS input and invokes
   `:domain:calling-notifications` synchronously through the split AVS framework; and
13. returns only a status, reason, bounded summary, and privacy-preserving presentation decision.

Decrypted notification candidates are not part of the public result. Until the versioned policy
snapshot and approved replacement behavior are wired, every result requires the
privacy-preserving fallback.

The entry point uses a dedicated `NotificationExtensionCoreLogic` account assembly. It does not
construct the application `CoreLogic` or `UserSessionScope`, so it does not run startup migrations,
continuous/slow sync, recovery workers, schedulers, pending confirmations, local event processing,
or the application calling lifecycle. Its CoreCrypto transporter rejects outbound MLS messages and
commits, and Proteus migration failure is returned without invoking application logout/data-clear
recovery. It requires a `NotificationExtensionCallProcessor`, so calling content cannot silently
take a no-op path. The processor bridge copies only scalar/string values into the separate
`KaliumNotificationExtensionAvs` framework, which runs the real `wcall_event_*` lifecycle and closes
the native processor before returning.

## Required host setup

The app must already have a valid logged-in account and registered client. Both the app and NSE
targets need:

- the same App Group entitlement;
- the same Keychain Sharing access group entitlement;
- the same App Group-derived `kaliumRootPath`;
- the same Keychain service name; and
- the same fully qualified Keychain access-group value.

The app's normal `CoreLogic` must also be configured with the shared access group:

```kotlin
ApplePersistenceConfig(
    serviceName = "<stable-keychain-service>",
    accessGroup = "<TEAM_ID.shared-keychain-group>",
    accessibleAfterFirstUnlock = true
)
```

Changing an existing installation to the after-first-unlock device-only accessibility class requires
an explicit update before the new class is included in every query. Kalium now performs that
service/access-group-scoped migration before constructing `KeychainSettings`. The main app must run
once while unlocked so the migration can access legacy when-unlocked items; otherwise a fresh login
is required before locked-device NSE testing. Moving entries from a different access group remains
a separate host migration.

## Swift-shaped usage

The exact generated Swift spelling should be taken from the built framework header. Both
`KaliumNotificationExtension` and `KaliumNotificationExtensionAvs` must be embedded as dynamic
frameworks. The required bridge is intentionally owned by Swift so no Kotlin object crosses between
their two runtimes:

```swift
final class RealAvsBridge: NSObject, NotificationExtensionCallProcessor {
    let callbacks: NotificationExtensionAvsCallbacks

    init(callbacks: NotificationExtensionAvsCallbacks) {
        self.callbacks = callbacks
    }

    func process(
        selfUserId: String,
        selfClientId: String,
        events: [NotificationExtensionCallEvent]
    ) -> NotificationExtensionCallProcessingStatus {
        let copied = events.map {
            NotificationExtensionAvsEvent(
                payload: String($0.payload),
                currentTimeSeconds: $0.currentTimeSeconds,
                messageTimeSeconds: $0.messageTimeSeconds,
                conversationId: String($0.conversationId),
                senderUserId: String($0.senderUserId),
                senderClientId: String($0.senderClientId),
                conversationType: avsConversationType($0.conversationType)
            )
        }
        let result = NotificationExtensionAvsProcessor().process(
            selfUserId: selfUserId,
            selfClientId: selfClientId,
            events: copied,
            callbacks: callbacks
        )
        switch result.status {
        case .success: return .success
        case .unsupportedPlatform: return .retryableFailure
        default: return .terminalFailure
        }
    }
}
```

`NotificationExtensionAvsCallbacks.onIncomingCall`, `onMissedCall`, and `onClosedCall` are the real
AVS-produced call-notification outputs. The host can inspect them for the spike and map them to its
CallKit/local-notification presentation layer.

The entry-point flow goes through the sole public production factory. The exact generated Swift
spelling must be taken from the built framework header; this source-shaped example intentionally
shows the security contract rather than bypassing construction:

```swift
let avsBridge = RealAvsBridge(callbacks: avsCallbacks)
let readiness = RealNotificationExtensionProductionReadiness(
    externallyVerifiedGateMask: externallyVerifiedGateMask,
    hostIntegrationReadiness: hostIntegrationReadiness
)
let construction = RealNotificationExtensionFactory.shared.createProduction(
    configuration: RealNotificationExtensionConfiguration(
        kaliumRootPath: sharedContainer.path,
        sharedAppGroupRoot: sharedContainer.path,
        keychainServiceName: keychainService,
        keychainAccessGroup: keychainAccessGroup,
        userAgent: userAgent
    ),
    callProcessor: avsBridge,
    readiness: readiness
)
guard construction.isAvailable, let component = construction.instance else {
    usePrivacyPreservingFallback()
    return
}

let request = RealNotificationExtensionRequest(
    userId: pushAccountId,
    absoluteDeadlineEpochMillis: deadlineEpochMillis
)

runHandle = component.begin(request: request, completion: completion)
```

The completion implementation inspects:

```swift
func complete(result_: RealNotificationExtensionResult) {
    let result = result_
    print("status=\(result.status) reason=\(result.reason)")
    print("frames=\(result.summary.transportFramesReceived)")
    guard result.shouldUsePrivacyPreservingFallback else { return }
    usePrivacyPreservingFallback()
}
```

Call `runHandle.cancelForExpiration()` from `serviceExtensionTimeWillExpire()`. The completion is
guarded so it is delivered at most once.

## Interpreting the result

- `status`, `reason`, and `summary` explain whether catch-up completed, hit a limit, lost the lock,
  reached the deadline, or deferred work to the foreground app.
- `presentationDecision` and `shouldUsePrivacyPreservingFallback` are the only presentation-facing
  outputs. Raw or decrypted message fields never cross the public boundary.
- Factory construction reports invalid configuration, missing host responsibilities, blocked
  production gates, and rejected attempts to claim code-owned gates as external evidence.

## Deliberate fail-closed behavior

The volatile inbox is no longer used. Real delivery tags reach the bounded engine and are accepted
only after the local writer durably stages the raw event in the encrypted-store implementation.
The App Group storage boundary now uses descriptor-relative/no-follow traversal, verifies inode
identity, effective-user ownership, type, link count and private modes, enforces complete-until-
first-authentication file protection, and stores handoff data in SQLCipher. The factory still
blocks production because notification policy and several code-owned/native/device gates remain
open. API v9 now retains the exact binary WebSocket frame before DTO decoding, and the NSE persists
the exact `data.event` value so unknown event fields survive without persisting transport delivery
tags.

There is also an unresolved CoreCrypto/handoff ordering window: child rows are committed while the
CoreCrypto transaction is still open. A crash after child staging but before CoreCrypto commit can
leave import-visible rows whose `cryptoStateApplied` claim is no longer true. Therefore
`CORE_CRYPTO_HANDOFF_CRASH_ORDERING` remains blocked and cannot be asserted through external
readiness. Production requires an explicit post-CoreCrypto-commit visibility compare-and-set before
the foreground importer may read those children.

The notification-only AVS bridge is also still code-blocked. Its callback currently runs while the
CoreCrypto transaction is open and before the child batch and crypto state are both known committed.
A rollback, cancellation, or crash can therefore repeat an externally visible AVS/CallKit side
effect. The bridge code itself satisfies `NOTIFICATION_AVS_SWIFT_BRIDGE`, but
`POST_COMMIT_IDEMPOTENT_AVS_DISPATCH` remains blocked until call work is represented by a durable,
idempotent post-commit outbox (or an equivalent compare-and-set) and is processed only after the
CoreCrypto commit and child visibility transition both succeed. `EXTENSION_SAFE_AVS_BINARY` is a
separate code-owned blocker while the current AVS artifact imports application-only UIKit APIs; it
is not externally claimable through a readiness mask.

`LOCAL_WRITER_ACK_GUARANTEE` also remains blocked. The API now rejects `trySend` failure, awaits
Ktor's `flush`, and checks that the session is still active, but Ktor documents that `flush` may
return immediately after termination. That primitive is not a definitive frame-write fence, so the
production factory cannot claim local-writer acceptance until the transport exposes one.

MLS welcomes, missing subconversation group metadata, delayed proposal commits, new CRL distribution
points, and any receive failure that needs active recovery return `FOREGROUND_RECOVERY_REQUIRED`.
They are not repaired or sent from the NSE.

## Remaining production work

- Replace the temporary `:logic:notification-extension -> :logic` dependency with a narrow
  module dependency so the dedicated passive assembly and its selected implementation classes live
  in the target lower-layer modules. The previous `UserSessionScope`-based iOS Simulator debug
  binary was 109,641,000 bytes. The passive-assembly version is 74,485,112 bytes, a reduction of
  35,155,888 bytes (32.1%); this remains a debug, non-App-Store-thinned measurement.
- Resolve the dedicated
  [AVS extension-safety production blocker](ios-nse-avs-extension-safety-blocker.md) by replacing
  the full `com.wire:avs-kmp` archive with an extension-safe notification-only AVS artifact. The
  current archive still imports `UIApplication`/`sharedApplication`, so it cannot pass App Store
  extension-safety validation even though the real AVS call path builds and runs.
- Add post-CoreCrypto-commit child visibility and a native main-database implementation of the
  exact-once foreground importer contract.
- Complete cursor cutover, recovery acknowledgement, account tombstoning, and downgrade ownership.
- Add a durable, idempotent post-commit call outbox so AVS never runs inside the CoreCrypto
  transaction and cannot repeat host-visible effects after rollback or restart.
- Wire the notification-policy snapshot and approved generic/replacement behavior.
- Complete signed physical-device testing for push delivery, locked-device Keychain accessibility,
  memory, cold start, expiration, owner death, real Proteus/MLS traffic, and captured real call
  payloads/AVS callbacks.
- Add host lifecycle and real push integration tests after the spike design is accepted.
