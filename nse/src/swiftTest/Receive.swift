import Foundation
import KaliumNSE

@main
struct ReceiveFixture {
    @MainActor static func main() async throws {
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
                budgetMilliseconds: 20_000,
                deliveredNotifications: [],
                syncMode: account["syncToLive"] as? Bool == true ? .toLive : .target
            )
            let result = try await receiver.process(request: request)
            precondition(result.status == .success, "Receive failed for \(account["route"]!)")
            if account["callingEvents"] as? Bool == true {
                precondition(result.calls.count == 1 && result.calls[0].type == .incoming, "Missing incoming call")
                let call = result.calls[0]
                precondition(call.conversationValue == "11111111-1111-4111-8111-111111111111", "Wrong call conversation")
                precondition(call.shouldRing, "Incoming call did not ring")
                // One-to-one calls use the sender's name, not the conversation's display name.
                // Message-preview restrictions never hide caller names.
                precondition(call.callerName == "Fixture",
                             "Wrong caller name: \(String(describing: call.callerName)) for \(account["route"]!)")
            } else {
                precondition(result.calls.isEmpty, "Unexpected call result")
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
            precondition(replay.status == .success && !replay.shouldNotify && replay.text == nil,
                         "Processed target alerted twice")
            if !result.calls.isEmpty {
                precondition(replay.calls.map(\.handoffId) == result.calls.map(\.handoffId),
                             "Unacknowledged call lost its durable identity on processed replay")
                precondition(replay.calls.allSatisfy { $0.handoffId != nil }, "Call has no durable handoff identity")
            }
            print("SWIFT_RECEIVE_PASSED \(account["mls"] as! Bool ? "MLS" : "Proteus")")
        }
    }
}
