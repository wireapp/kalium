/* Disposable native reference for ADR 0010 Milestone 8. No Kalium framework dependency. */

import CryptoKit
import Foundation

let foregroundImportProtocolVersionV1 = 1
let notificationInboxContractVersionV2 = 2
let syntheticInboxAccountV1 = "synthetic-notification-inbox-account"
let syntheticInboxClientV1 = "synthetic-notification-inbox-client"
let foregroundQueuedReasonV1 = "FOREGROUND_IMPORT_DURABLY_QUEUED_V1"

enum HandoffContractError: Error {
    case incompatibleSchema
    case corruptState
    case integrityConflict
    case invalidDisposition
    case lockUnavailable
    case sqlite(String)
    case injectedMainFailure
}

struct HandoffScopeV1: Equatable {
    let accountID: String
    let clientID: String
}

enum ForegroundImportActionV1: String {
    case upsertApplicationMessage = "UPSERT_APPLICATION_MESSAGE"
    case recordCryptoStateAlreadyApplied = "RECORD_CRYPTO_STATE_ALREADY_APPLIED"
    case recordCompletion = "RECORD_COMPLETION"
    case recordTerminalFailure = "RECORD_TERMINAL_FAILURE"
    case scheduleForegroundRecovery = "SCHEDULE_FOREGROUND_RECOVERY"
}

enum RawDispositionV1 {
    case processedByForeground
    case durablyQueuedForForeground
}

struct RawImportV1 {
    let envelope: Data
    let action: ForegroundImportActionV1
}

struct ChildImportV1 {
    let childSequence: Int64
    let parentIngestSequence: Int64
    let parentServerEventID: String
    let itemIndex: Int64
    let idempotencyKey: String
    let conversationID: String?
    let senderID: String?
    let senderClientID: String?
    let receiveProtocol: String
    let messageTimestampMillis: Int64?
    let decryptedProto: Data?
    let decryptedProtoSHA256: String?
    let cryptoStateApplied: Bool
    let receiveClassification: String
    let failureClassification: String?
    let decryptionState: String
    let notificationState: String
    let importState: String
    let retryCount: Int64
    let action: ForegroundImportActionV1
    let recordToken: String
}

struct ForegroundImportUnitV1 {
    let scope: HandoffScopeV1
    let parentIngestSequence: Int64
    let parentServerEventID: String
    let rawEnvelopeSHA256: String
    let rawEnvelopeFormatVersion: Int64
    let serverTimestampMillis: Int64?
    let isTransient: Bool
    let associatedCursor: String?
    let deliverySource: String
    let receivedAtMillis: Int64
    let receiveState: String
    let foregroundRecoveryRequired: Bool
    let recoveryReason: String?
    let importState: String
    let rawImport: RawImportV1?
    let children: [ChildImportV1]
    let parentRecordToken: String
}

struct ForegroundImportSnapshotV1 {
    let protocolVersion: Int
    let snapshotMaxIngestSequence: Int64
    let unit: ForegroundImportUnitV1
    let hasMore: Bool
    let snapshotToken: String
    let readerIdentity: UUID
}

enum HandoffMarkResultV1 {
    case marked(rawParents: Int, children: Int)
    case alreadyImported
}

func sha256HexV1(_ data: Data) -> String {
    SHA256.hash(data: data).map { String(format: "%02x", $0) }.joined()
}

func fallbackChildIdempotencyKeyV1(serverEventID: String, itemIndex: Int64) -> String {
    var data = Data("com.wire.kalium.notification-inbox-child/v1".utf8)
    let event = Data(serverEventID.utf8)
    var eventSize = UInt32(event.count).bigEndian
    withUnsafeBytes(of: &eventSize) { data.append(contentsOf: $0) }
    data.append(event)
    var index = UInt32(itemIndex).bigEndian
    withUnsafeBytes(of: &index) { data.append(contentsOf: $0) }
    return "fallback-v1:\(sha256HexV1(data))"
}

func foregroundFrameDigestV1(prefix: String, fields: [String?]) -> String {
    var frame = Data()
    for value in [prefix] + fields {
        guard let value else {
            appendBigEndianInt32V1(-1, to: &frame)
            continue
        }
        let bytes = Data(value.utf8)
        appendBigEndianInt32V1(Int32(bytes.count), to: &frame)
        frame.append(bytes)
    }
    return sha256HexV1(frame)
}

func childActionV1(
    classification: String,
    decryptionState: String,
    cryptoStateApplied: Bool,
    hasDecryptedProto: Bool
) throws -> ForegroundImportActionV1 {
    if decryptionState == "FAILED_TERMINAL" { return .recordTerminalFailure }
    if classification == "APPLICATION_MESSAGE" {
        guard decryptionState == "DECRYPTED", cryptoStateApplied, hasDecryptedProto else {
            throw HandoffContractError.corruptState
        }
        return .upsertApplicationMessage
    }
    if classification == "HANDSHAKE_ONLY" && cryptoStateApplied {
        return .recordCryptoStateAlreadyApplied
    }
    if classification == "WELCOME" || classification == "UNSUPPORTED" || classification == "HANDSHAKE_ONLY" {
        return .scheduleForegroundRecovery
    }
    return .recordCompletion
}

func childRecordTokenV1(_ child: ChildImportV1, scope: HandoffScopeV1) -> String {
    foregroundFrameDigestV1(
        prefix: "com.wire.kalium.notification-inbox.foreground-child/v1",
        fields: [
            scope.accountID,
            scope.clientID,
            String(child.childSequence),
            String(child.parentIngestSequence),
            child.parentServerEventID,
            String(child.itemIndex),
            child.idempotencyKey,
            child.conversationID,
            child.senderID,
            child.senderClientID,
            child.receiveProtocol,
            child.messageTimestampMillis.map(String.init),
            child.decryptedProtoSHA256,
            child.cryptoStateApplied ? "1" : "0",
            child.receiveClassification,
            child.failureClassification,
            child.decryptionState,
            child.notificationState,
            String(child.retryCount),
            child.action.rawValue,
        ]
    )
}

func parentRecordTokenV1(_ unit: ForegroundImportUnitV1) -> String {
    foregroundFrameDigestV1(
        prefix: "com.wire.kalium.notification-inbox.foreground-parent/v1",
        fields: [
            unit.scope.accountID,
            unit.scope.clientID,
            String(unit.parentIngestSequence),
            unit.parentServerEventID,
            unit.rawEnvelopeSHA256,
            String(unit.rawEnvelopeFormatVersion),
            unit.serverTimestampMillis.map(String.init),
            unit.isTransient ? "1" : "0",
            unit.associatedCursor,
            unit.deliverySource,
            String(unit.receivedAtMillis),
            unit.receiveState,
            unit.foregroundRecoveryRequired ? "1" : "0",
            unit.recoveryReason,
            unit.rawImport == nil ? "0" : "1",
            unit.rawImport?.action.rawValue,
        ]
    )
}

func snapshotTokenV1(maxIngestSequence: Int64, unit: ForegroundImportUnitV1) -> String {
    var fields: [String?] = [
        String(foregroundImportProtocolVersionV1),
        unit.scope.accountID,
        unit.scope.clientID,
        String(maxIngestSequence),
        "0",
        String(unit.parentIngestSequence),
        parentRecordTokenV1(unit),
        String(unit.children.count),
    ]
    for child in unit.children {
        fields.append(String(child.childSequence))
        fields.append(child.recordToken)
    }
    return foregroundFrameDigestV1(
        prefix: "com.wire.kalium.notification-inbox.foreground-snapshot/v1",
        fields: fields
    )
}

func globalRecoveryTokenV1(scope: HandoffScopeV1, reason: String, recordedAtMillis: Int64) -> String {
    foregroundFrameDigestV1(
        prefix: "com.wire.kalium.notification-inbox.global-recovery/v1",
        fields: [scope.accountID, scope.clientID, reason, String(recordedAtMillis)]
    )
}

func accountTombstoneTokenV1(
    scope: HandoffScopeV1,
    removalID: String,
    reason: String,
    tombstonedAtMillis: Int64
) -> String {
    foregroundFrameDigestV1(
        prefix: "com.wire.kalium.notification-inbox.account-tombstone/v1",
        fields: [scope.accountID, scope.clientID, removalID, reason, String(tombstonedAtMillis)]
    )
}

private func appendBigEndianInt32V1(_ value: Int32, to data: inout Data) {
    var bigEndian = value.bigEndian
    withUnsafeBytes(of: &bigEndian) { data.append(contentsOf: $0) }
}
