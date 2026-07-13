/* Disposable simulator host for ADR 0010 Milestone 7. */

import Foundation
import KaliumNotificationExtension
import KaliumNotificationExtensionAvs

let sharedRoot = (NSTemporaryDirectory() as NSString)
    .appendingPathComponent("kalium-notification-extension-m7-(ProcessInfo.processInfo.processIdentifier)")

final class NoOpAvsCallbacks: NSObject, NotificationExtensionAvsCallbacks {
    func onIncomingCall(incomingCall: NotificationExtensionAvsIncomingCall) {}
    func onMissedCall(missedCall: NotificationExtensionAvsMissedCall) {}
    func onClosedCall(closedCall: NotificationExtensionAvsClosedCall) {}
}

final class SplitAvsCallProcessor: NSObject, NotificationExtensionCallProcessor {
    private(set) var detail = "facadeReturned=false; realPayload=false"

    func process(events: [NotificationExtensionCallEvent]) -> NotificationExtensionCallProcessingStatus {
        let copiedEvents = events.map { event in
            NotificationExtensionAvsEvent(
                payload: String(event.payload),
                currentTimeSeconds: event.currentTimeSeconds,
                messageTimeSeconds: event.messageTimeSeconds,
                conversationId: String(event.conversationId),
                senderUserId: String(event.senderUserId),
                senderClientId: String(event.senderClientId),
                conversationType: conversationType(event.conversationType)
            )
        }
        let result = NotificationExtensionAvsProcessor().process(
            selfUserId: "synthetic-avs-user",
            selfClientId: "synthetic-avs-client",
            events: copiedEvents,
            callbacks: NoOpAvsCallbacks()
        )
        detail = "facadeReturned=\(result.status); realPayload=false"
        switch result.status {
        case .success:
            return .success
        case .unsupportedPlatform:
            return .retryableFailure
        default:
            return .terminalFailure
        }
    }

    private func conversationType(_ value: Int32) -> NotificationExtensionAvsConversationType {
        switch value {
        case 0: return .oneOnOne
        case 1: return .group
        case 2: return .conference
        case 3: return .conferenceMls
        default: return .unknown
        }
    }
}

let callProcessor = SplitAvsCallProcessor()

NotificationExtensionFrameworkProbe().run(sharedRoot: sharedRoot, callProcessor: callProcessor) { result, error in
    if let error {
        NSLog("milestone7Framework=false error=\(error)")
        exit(1)
    }
    guard let result else {
        NSLog("milestone7Framework=false error=nil-result")
        exit(1)
    }
    NSLog(
        "milestone7Framework=\(result.passed) completionCount=\(result.completionCount) " +
        "immediateExpiration=\(result.immediateExpirationCompletion) " +
        "stageBeforeAck=\(result.stageBeforeAck) lockHeldForStorage=\(result.lockHeldForStorage) " +
        "storeClosedBeforeRelease=\(result.storeClosedBeforeRelease) exactProto=\(result.exactProto) " +
        "genericFallback=\(result.genericFallback) productionAvailable=\(result.productionAvailable) " +
        "avsBridgeUnderLock=\(result.avsBridgeUnderLock) avsFacadeReturned=\(result.avsFacadeReturned) " +
        "realNetwork=\(result.realNetwork) realCrypto=\(result.realCrypto) realAvs=\(result.realAvs) " +
        "avsDetail=\(callProcessor.detail) detail=\(result.detail)"
    )
    exit(result.passed ? 0 : 1)
}

RunLoop.current.run()
