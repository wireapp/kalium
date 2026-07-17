# iOS NSE real-account integration spike

Date: 2026-07-18

Status: implemented for signed host-app/NSE integration testing; not production-ready.

## What this path does

`RealNotificationExtension` is the temporary real-account entry point in the
`KaliumNotificationExtension` framework. The caller supplies the qualified Wire account and an
absolute deadline. Kalium then:

1. opens the same App Group-backed Kalium root used by the logged-in app;
2. reads auth state through the same Keychain service and access group;
3. resolves the account's locally registered client ID;
4. acquires the non-blocking account/client process lock;
5. opens the authenticated consumable-notification WebSocket with a new synchronization marker;
6. performs one bounded catch-up and closes at the matching marker;
7. applies real Proteus or MLS receive bytes to the existing CoreCrypto state;
8. resolves Proteus external content, decodes the exact `GenericMessage` protobuf, and runs the
   notification-content extractor; and
9. returns the decrypted candidates in `RealNotificationExtensionResult.notifications`.

It does not start `IncrementalSyncManager`, slow sync, message sending, chat receipts, MLS recovery,
or a continuous WebSocket listener.

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
    accessGroup = "<TEAM_ID.shared-keychain-group>"
)
```

Changing an existing installation from the default target-specific Keychain group to a shared
group does not make old entries appear in the new group. The host must migrate the existing entries
or perform a fresh login before testing the NSE.

## Swift-shaped usage

The exact generated Swift spelling should be taken from the built framework header, but the host
flow is:

```swift
let component = RealNotificationExtension(
    configuration: RealNotificationExtensionConfiguration(
        kaliumRootPath: sharedContainer.appendingPathComponent("kalium").path,
        sharedAppGroupRoot: sharedContainer.path,
        keychainServiceName: keychainService,
        keychainAccessGroup: keychainAccessGroup,
        userAgent: userAgent
    )
)

let request = RealNotificationExtensionRequest(
    userId: pushAccountId,
    userDomain: pushAccountDomain,
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

    for notification in result.notifications {
        print("kind=\(notification.kind) message=\(notification.messageId)")
        print("conversation=\(notification.conversationId)@\(notification.conversationDomain)")
        print("body=\(notification.body ?? "<generic>")")
    }
}
```

Call `runHandle.cancelForExpiration()` from `serviceExtensionTimeWillExpire()`. The completion is
guarded so it is delivered at most once.

## Interpreting the result

- `notifications` is the list produced by real decryption and pure extraction. It is the answer to
  “which notifications did this invocation produce?”
- `status`, `reason`, and `summary` explain whether catch-up completed, hit a limit, lost the lock,
  reached the deadline, or deferred work to the foreground app.
- `clientId` is the locally registered client Kalium resolved for the requested account.
- `markerId` identifies only this bounded WebSocket session.
- `shouldUsePrivacyPreservingFallback` remains `true` because this spike does not yet consume the
  versioned notification-policy snapshot. Showing `body` is appropriate only for the explicit
  integration demonstration until the host policy is wired.

## Deliberate spike safety behavior

Real transport delivery tags are not acknowledged in this assembly. Events are staged only in a
volatile per-invocation inbox, and claiming a durable transport ACK would risk message loss if iOS
killed the NSE. The backend can therefore redeliver the same events. The bounded engine still
enforces stage-before-process ordering, exact duplicate checks, frame/byte/batch budgets, deadline
checks, and non-blocking process-lock acquisition.

This avoids writing real plaintext notification data to the existing synthetic SQLite handoff
store. Production transport ACKs remain blocked until the App Group handoff is encrypted, durable,
and has a socket-writer acceptance guarantee.

MLS welcomes, missing subconversation group metadata, delayed proposal commits, new CRL distribution
points, and any receive failure that needs active recovery return `FOREGROUND_RECOVERY_REQUIRED`.
They are not repaired or sent from the NSE.

## Remaining production work

- Replace the temporary `:logic:notification-extension -> :logic` dependency with a narrow
  authenticated account/CoreCrypto assembly so the framework is actually lightweight. The linked
  iOS Simulator debug binary for this spike is 109,641,000 bytes; this is not an App Store-thinned
  measurement, but it makes the temporary packaging cost explicit.
- Open all shared account/CoreCrypto resources only after acquiring the cross-process lock, and
  make the foreground app use the same lock for overlapping work.
- Add encrypted durable handoff persistence, cursor cutover, safe transport ACKs, and native
  foreground import.
- Wire the notification-policy snapshot and approved generic/replacement behavior.
- Complete signed physical-device testing for push delivery, locked-device Keychain accessibility,
  memory, cold start, expiration, owner death, and real Proteus/MLS/AVS traffic.
- Add automated tests only after the spike design is accepted, per the current working agreement.
