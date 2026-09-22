import Foundation
import KaliumNSE

@main
struct ReceiveFixture {
    @MainActor static func main() async throws {
        try await verifyCallKitReporting()
        let data = try Data(contentsOf: URL(fileURLWithPath: CommandLine.arguments[1]))
        let accounts = try JSONSerialization.jsonObject(with: data) as! [[String: Any]]
        for account in accounts {
            let configuration = KaliumNSEConfiguration(
                rootPath: account["root"] as! String,
                keychainService: account["service"] as! String,
                keychainAccessGroup: nil,
                keychainAccessibility: .whenUnlocked,
                userAgent: "KaliumNSE-Swift-fixture",
                certificatePins: [:],
                guestRoomLink: true,
                selfDeletingMessages: true, drivePermissionsEnabled: false,
                linkPreviewEnabled: account["linkPreviewEnabled"] as? Bool ?? false,
                allowedFileExtensions: account["allowedFileExtensions"] as? [String]
            )
            let receiver = KaliumNSEReceiver(configuration: configuration)
            let showContent = !(account["callingEvents"] as? Bool == true && account["mls"] as? Bool == true)
            let request = KaliumNSERequest(
                userValue: account["userValue"] as! String,
                userDomain: account["userDomain"] as! String,
                notificationId: "notification",
                showMessageContent: showContent,
                budgetMilliseconds: 20_000
            )
            let accountID = UUID(uuidString: "22222222-2222-4222-8222-222222222222")!
            var reportedCalls: [[String: Any]] = []
            let callReporter = KaliumNSECallKitReporter { payload in
                await Task.yield()
                reportedCalls.append(payload)
            }
            let result = try await callReporter.process(receiver: receiver, request: request, accountID: accountID)
            precondition(result.status == .success, "Receive failed for \(account["route"]!)")
            if account["callingEvents"] as? Bool == true {
                precondition(result.calls.count == 1 && result.calls[0].type == .incoming, "Missing incoming call")
                precondition(reportedCalls.count == 1, "CallKit reporting was not awaited")
                let payload = reportedCalls[0]
                precondition(payload["accountID"] as? String == accountID.uuidString, "Wrong CallKit account")
                precondition(payload["conversationID"] as? String == "11111111-1111-4111-8111-111111111111", "Wrong call conversation")
                precondition(payload["shouldRing"] as? Bool == true, "Incoming call did not ring")
                // One-to-one calls use the sender's name, not the conversation's display name.
                precondition(payload["callerName"] as? String == (showContent ? "Fixture" : ""),
                             "Wrong caller privacy: \(String(describing: payload["callerName"])) for \(account["route"]!)")
            } else {
                precondition(result.calls.isEmpty && reportedCalls.isEmpty, "Unexpected call reporting")
            }
            if account["sentConnectionEvents"] as? Bool == true || account["acceptedConnectionEvents"] as? Bool == true {
                precondition(result.contentType == .message && result.shouldNotify && result.messageId == "first",
                             "Missing text after connection lifecycle work")
                precondition(result.text == nil, "Degraded connection exposed a body preview")
            } else if account["connectionEvents"] as? Bool == true {
                precondition(result.contentType == .connectionRequest && result.shouldNotify && result.text == nil,
                             "Missing connection request decision")
                precondition(result.messageId == "" && result.conversationValue == "request" && result.senderName == "Peer request",
                             "Connection request lost its presentation fields")
            } else if account["conversationCleanup"] as? String == "delete" {
                precondition(result.contentType == .conversationDeleted && result.shouldNotify && result.text == nil,
                             "Missing conversation deletion decision")
                precondition(result.conversationValue == "delete-target" && result.conversationName == "Fixture" && result.senderName != nil,
                             "Deletion lost its presentation names")
            } else if account["readState"] as! Bool {
                precondition(!result.shouldNotify && result.text == nil, "Self-device synchronization alerted")
            } else if account["assetEvents"] as? Bool == true {
                precondition(result.shouldNotify && result.messageId == "last-image" && result.text == nil,
                             "Missing generic asset notification")
            } else if account["contentEvents"] as? Bool == true {
                precondition(result.shouldNotify && result.messageId == "location" && result.text == nil,
                             "Missing generic location notification")
            } else {
                let expectedText = account["expectedText"] as? String ?? "first"
                precondition(result.shouldNotify && result.messageId == expectedText, "Missing eligible text for \(account["route"]!)")
                if !showContent || account["membershipEvents"] as? Bool == true || account["creationEvents"] as? Bool == true ||
                    account["recoveryEvents"] as? Bool == true || account["resetEvents"] as? Bool == true ||
                    account["migrationEvents"] as? Bool == true || account["oneOnOneEvents"] as? Bool == true ||
                    account["identityEvents"] as? Bool == true || account["x509PeerRevoked"] as? Bool == true || account["federationEvents"] as? Bool == true ||
                    account["meetingConversationEvents"] as? Bool == true || account["e2eiSettingsEvents"] as? Bool == true ||
                    account["memberRoleFetched"] as? Bool == true ||
                    (account["mlsClientEvents"] as? Bool == true && (account["route"] as! String).hasSuffix("-read")) {
                    // Membership triggers mark the fixture's unverified clients degraded.
                    precondition(result.text == nil, "Degraded conversation exposed a body preview for \(account["route"]!)")
                } else {
                    precondition(result.text == expectedText, "Missing body preview for \(account["route"]!)")
                }
            }
            let replay = try await receiver.process(request: request)
            precondition(replay.status == .success && !replay.shouldNotify && replay.text == nil && replay.calls.isEmpty,
                         "Processed target alerted twice")
            print("SWIFT_RECEIVE_PASSED \(account["mls"] as! Bool ? "MLS" : "Proteus")")
        }
    }

    @MainActor static func verifyCallKitReporting() async throws {
        let first = "11111111-1111-4111-8111-111111111111"
        let second = "33333333-3333-4333-8333-333333333333"
        let accountID = UUID()
        func call(_ type: KaliumNSECallType, _ conversation: String, ring: Bool = false) -> KaliumNSECall {
            KaliumNSECall(type: type, conversationValue: conversation, conversationDomain: "example.com",
                          callerValue: "caller", callerDomain: "example.com", callerClientId: "client",
                          messageTimeSeconds: 100, shouldRing: ring, isVideoCall: true, closeReason: nil, callerName: nil)
        }
        var payloads: [[String: Any]] = []
        let reporter = KaliumNSECallKitReporter { payload in
            await Task.yield()
            payloads.append(payload)
        }
        try await reporter.report([
            call(.closed, first), call(.incoming, first, ring: true), call(.incoming, second, ring: true),
            call(.missed, first), call(.closed, second), call(.closed, second)
        ], accountID: accountID)
        precondition(payloads.count == 4, "CallKit must close only conversations reported during this request")
        precondition(payloads.map { $0["shouldRing"] as! Bool } == [true, true, false, false], "Wrong call action order")
        precondition(payloads[0]["callerName"] as? String == "", "Private caller name was not hidden")
        precondition(payloads[0]["hasVideo"] as? Bool == true, "Video flag was lost")
        enum Failure: Error { case reporting }
        let failing = KaliumNSECallKitReporter { _ in throw Failure.reporting }
        do {
            try await failing.report([call(.incoming, first, ring: true)], accountID: accountID)
            preconditionFailure("Reporting failure was swallowed")
        } catch Failure.reporting { }
        let cancelled = Task { @MainActor in
            try await reporter.report([call(.incoming, first, ring: true)], accountID: accountID)
        }
        cancelled.cancel()
        do {
            try await cancelled.value
            preconditionFailure("Cancelled reporting continued")
        } catch is CancellationError { }
        precondition(payloads.count == 4, "Cancelled reporting emitted a payload")
    }
}
