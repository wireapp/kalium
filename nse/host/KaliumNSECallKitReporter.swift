// Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later
import CallKit
import Foundation
import KaliumNSE

/// Include this file in the host NSE target. The host supplies its own account UUID.
/// Await this flow before invoking the notification service's completion handler.
@MainActor
public struct KaliumNSECallKitReporter {
    private let reportPayload: ([String: Any]) async throws -> Void

    public init() {
        reportPayload = { payload in
            try await CXProvider.reportNewIncomingVoIPPushPayload(payload)
        }
    }

    /// Injectable reporting for host integration tests.
    public init(reportPayload: @escaping ([String: Any]) async throws -> Void) {
        self.reportPayload = reportPayload
    }

    public func process(
        receiver: KaliumNSEReceiver,
        request: KaliumNSERequest,
        accountID: UUID
    ) async throws -> KaliumNSEResult {
        let result = try await receiver.process(request: request)
        if result.status == .success {
            try await report(result.calls, accountID: accountID, accountDomain: request.userDomain)
        }
        return result
    }

    public func report(_ calls: [KaliumNSECall], accountID: UUID, accountDomain: String) async throws {
        guard !accountDomain.isEmpty else { throw ReportingError.missingAccountDomain }
        // This is a transport adapter, not the host call register. An end signal can
        // arrive in a different push/process from its start. Forward every decision;
        // the host must deduplicate and correlate it before touching CallKit.
        for call in calls {
            try Task.checkCancellation()
            guard let conversationID = UUID(uuidString: call.conversationValue) else {
                throw ReportingError.invalidConversationID
            }
            let shouldRing: Bool
            let eventType: String
            switch call.type {
            case .incoming:
                shouldRing = call.shouldRing
                eventType = "incoming"
            case .missed:
                shouldRing = false
                eventType = "missed"
            case .closed:
                shouldRing = false
                eventType = "closed"
            default:
                throw ReportingError.unsupportedCallType
            }
            // Keep the legacy required fields on *every* event, including termination.
            // Additional evidence is for the new host parser; it is not a call UUID.
            var payload: [String: Any] = [
                "callPayloadVersion": 1,
                "accountID": accountID.uuidString,
                "accountDomain": accountDomain,
                "conversationID": conversationID.uuidString,
                "conversationDomain": call.conversationDomain,
                "eventType": eventType,
                "messageTimeSeconds": call.messageTimeSeconds,
                "callerID": call.callerValue,
                "callerDomain": call.callerDomain,
                "shouldRing": shouldRing,
                "hasVideo": call.isVideoCall,
                "callerName": call.callerName ?? ""
            ]
            if let handoffID = call.handoffId { payload["handoffID"] = handoffID }
            if let clientID = call.callerClientId { payload["callerClientID"] = clientID }
            if let reason = call.closeReason { payload["closeReason"] = reason.intValue }
            try await reportPayload(payload)
        }
        try Task.checkCancellation()
    }

    public enum ReportingError: Error {
        case missingAccountDomain
        case invalidConversationID
        case unsupportedCallType
    }
}
