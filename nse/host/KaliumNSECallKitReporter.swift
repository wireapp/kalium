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
            try await report(result.calls, accountID: accountID)
        }
        return result
    }

    public func report(_ calls: [KaliumNSECall], accountID: UUID) async throws {
        // Like the iOS NSE branch, only stop a ring reported during this request.
        // Track each qualified conversation separately when AVS emits several callbacks.
        var ringing = Set<String>()
        for call in calls {
            try Task.checkCancellation()
            guard let conversationID = UUID(uuidString: call.conversationValue) else {
                throw ReportingError.invalidConversationID
            }
            let key = "\(conversationID.uuidString.lowercased())@\(call.conversationDomain.lowercased())"
            switch call.type {
            case .incoming:
                try await reportPayload([
                    "accountID": accountID.uuidString,
                    "conversationID": conversationID.uuidString,
                    "shouldRing": call.shouldRing,
                    "hasVideo": call.isVideoCall,
                    "callerName": call.callerName ?? ""
                ])
                if call.shouldRing {
                    ringing.insert(key)
                } else {
                    ringing.remove(key)
                }
            case .missed, .closed:
                guard ringing.contains(key) else { continue }
                try await reportPayload([
                    "accountID": accountID.uuidString,
                    "conversationID": conversationID.uuidString,
                    "shouldRing": false
                ])
                ringing.remove(key)
            default:
                break
            }
        }
        try Task.checkCancellation()
    }

    public enum ReportingError: Error {
        case invalidConversationID
    }
}
