/* Synthetic app-owned database. Production native schema/side effects remain outside this repo. */

import Foundation
import SQLite3

struct MainDatabaseCommitReceiptV1 {
    let snapshotToken: String
    let exactReplay: Bool
}

protocol ForegroundMainStoreV1 {
    func importAtomically(
        snapshot: ForegroundImportSnapshotV1,
        injectFailureBeforeCommit: Bool
    ) throws -> MainDatabaseCommitReceiptV1
}

final class SyntheticNativeMainStoreV1: ForegroundMainStoreV1 {
    private let database: SQLiteConnectionV1

    init(path: String) throws {
        database = try SQLiteConnectionV1(syntheticPlaintextPath: path, createIfMissing: true)
        try database.execute(
            "CREATE TABLE IF NOT EXISTS ImportLedger(" +
                "record_key TEXT PRIMARY KEY, payload_token TEXT NOT NULL, action TEXT NOT NULL)"
        )
        try database.execute(
            "CREATE TABLE IF NOT EXISTS SyntheticMessage(" +
                "message_id TEXT NOT NULL, conversation_id TEXT NOT NULL, proto_sha256 TEXT NOT NULL, " +
                "proto_blob BLOB NOT NULL, message_timestamp_millis INTEGER NOT NULL, " +
                "parent_ingest_sequence INTEGER NOT NULL, item_index INTEGER NOT NULL, " +
                "idempotency_key TEXT NOT NULL, " +
                "PRIMARY KEY(message_id, conversation_id))"
        )
        try database.execute(
            "CREATE TABLE IF NOT EXISTS DeferredWork(" +
                "record_key TEXT PRIMARY KEY, payload_token TEXT NOT NULL, reason TEXT NOT NULL, " +
                "account_id TEXT, client_id TEXT, parent_ingest_sequence INTEGER, server_event_id TEXT, " +
                "raw_envelope_sha256 TEXT, raw_envelope_format_version INTEGER, raw_envelope BLOB)"
        )
    }

    func close() { database.close() }

    func importAtomically(
        snapshot: ForegroundImportSnapshotV1,
        injectFailureBeforeCommit: Bool
    ) throws -> MainDatabaseCommitReceiptV1 {
        try database.execute("BEGIN IMMEDIATE")
        do {
            var insertedLedgerCount = 0
            for child in snapshot.unit.children {
                let key = childLedgerKey(child, scope: snapshot.unit.scope)
                let inserted = try insertOrVerifyLedger(
                    key: key,
                    payloadToken: child.recordToken,
                    action: child.action.rawValue
                )
                if inserted { insertedLedgerCount += 1 }
                try applyChild(child, ledgerKey: key, allowEffectInsert: inserted)
            }
            if let raw = snapshot.unit.rawImport {
                let key = rawLedgerKey(snapshot.unit)
                let inserted = try insertOrVerifyLedger(
                    key: key,
                    payloadToken: snapshot.unit.parentRecordToken,
                    action: raw.action.rawValue
                )
                if inserted { insertedLedgerCount += 1 }
                if raw.action == .scheduleForegroundRecovery {
                    try insertOrVerifyRawDeferred(
                        key: key,
                        unit: snapshot.unit,
                        raw: raw,
                        reason: foregroundQueuedReasonV1,
                        allowInsert: inserted
                    )
                }
            }
            if injectFailureBeforeCommit { throw HandoffContractError.injectedMainFailure }
            try database.execute("COMMIT")
            return MainDatabaseCommitReceiptV1(
                snapshotToken: snapshot.snapshotToken,
                exactReplay: insertedLedgerCount == 0
            )
        } catch {
            try? database.execute("ROLLBACK")
            throw error
        }
    }

    func ledgerCount() throws -> Int { try scalarCount("ImportLedger") }
    func messageCount() throws -> Int { try scalarCount("SyntheticMessage") }
    func deferredCount() throws -> Int { try scalarCount("DeferredWork") }

    func deferredRawPayloadCount() throws -> Int {
        let statement = try database.statement("SELECT COUNT(*) FROM DeferredWork WHERE raw_envelope IS NOT NULL")
        guard try statement.stepRow() else { throw HandoffContractError.corruptState }
        return Int(statement.int64(0))
    }

    func messageIDsInUIOrder() throws -> [String] {
        let statement = try database.statement(
            "SELECT message_id FROM SyntheticMessage ORDER BY message_timestamp_millis ASC, " +
                "parent_ingest_sequence ASC, item_index ASC, idempotency_key ASC"
        )
        var values: [String] = []
        while try statement.stepRow() {
            guard let value = statement.text(0) else { throw HandoffContractError.corruptState }
            values.append(value)
        }
        return values
    }

    func proveConflict(recordKey: String) throws {
        try database.execute("BEGIN IMMEDIATE")
        do {
            _ = try insertOrVerifyLedger(
                key: recordKey,
                payloadToken: String(repeating: "f", count: 64),
                action: ForegroundImportActionV1.upsertApplicationMessage.rawValue
            )
            throw HandoffContractError.corruptState
        } catch HandoffContractError.integrityConflict {
            try database.execute("ROLLBACK")
            return
        } catch {
            try? database.execute("ROLLBACK")
            throw error
        }
    }

    private func applyChild(_ child: ChildImportV1, ledgerKey: String, allowEffectInsert: Bool) throws {
        switch child.action {
        case .upsertApplicationMessage:
            guard child.idempotencyKey.hasPrefix("message-uid:v1:"),
                  let conversation = child.conversationID,
                  let proto = child.decryptedProto,
                  let protoHash = child.decryptedProtoSHA256,
                  let timestamp = child.messageTimestampMillis else {
                throw HandoffContractError.corruptState
            }
            let messageID = String(child.idempotencyKey.dropFirst("message-uid:v1:".count))
            try insertOrVerifyMessage(
                messageID: messageID,
                conversationID: conversation,
                proto: proto,
                protoHash: protoHash,
                timestamp: timestamp,
                parentIngestSequence: child.parentIngestSequence,
                itemIndex: child.itemIndex,
                idempotencyKey: child.idempotencyKey,
                allowInsert: allowEffectInsert
            )
        case .scheduleForegroundRecovery:
            try insertOrVerifyDeferredMarker(
                key: ledgerKey,
                payloadToken: child.recordToken,
                reason: "CHILD_REQUIRES_FOREGROUND_RECOVERY_V1",
                allowInsert: allowEffectInsert
            )
        case .recordCryptoStateAlreadyApplied, .recordCompletion, .recordTerminalFailure:
            break
        }
    }

    private func insertOrVerifyLedger(key: String, payloadToken: String, action: String) throws -> Bool {
        let select = try database.statement(
            "SELECT payload_token, action FROM ImportLedger WHERE record_key = ?"
        )
        try select.bind(1, key)
        if try select.stepRow() {
            guard select.text(0) == payloadToken, select.text(1) == action else {
                throw HandoffContractError.integrityConflict
            }
            return false
        }
        let insert = try database.statement(
            "INSERT INTO ImportLedger(record_key, payload_token, action) VALUES(?, ?, ?)"
        )
        try insert.bind(1, key)
        try insert.bind(2, payloadToken)
        try insert.bind(3, action)
        _ = try insert.stepRow()
        guard database.changes == 1 else { throw HandoffContractError.integrityConflict }
        return true
    }

    private func insertOrVerifyMessage(
        messageID: String,
        conversationID: String,
        proto: Data,
        protoHash: String,
        timestamp: Int64,
        parentIngestSequence: Int64,
        itemIndex: Int64,
        idempotencyKey: String,
        allowInsert: Bool
    ) throws {
        let select = try database.statement(
            "SELECT proto_sha256, proto_blob, message_timestamp_millis, parent_ingest_sequence, " +
                "item_index, idempotency_key FROM SyntheticMessage " +
                "WHERE message_id = ? AND conversation_id = ?"
        )
        try select.bind(1, messageID)
        try select.bind(2, conversationID)
        if try select.stepRow() {
            guard select.text(0) == protoHash, select.data(1) == proto, select.int64(2) == timestamp,
                  select.int64(3) == parentIngestSequence, select.int64(4) == itemIndex,
                  select.text(5) == idempotencyKey else {
                throw HandoffContractError.integrityConflict
            }
            return
        }
        guard allowInsert else { throw HandoffContractError.integrityConflict }
        let insert = try database.statement(
            "INSERT INTO SyntheticMessage(message_id, conversation_id, proto_sha256, proto_blob, " +
                "message_timestamp_millis, parent_ingest_sequence, item_index, idempotency_key) " +
                "VALUES(?, ?, ?, ?, ?, ?, ?, ?)"
        )
        try insert.bind(1, messageID)
        try insert.bind(2, conversationID)
        try insert.bind(3, protoHash)
        try insert.bind(4, proto)
        try insert.bind(5, timestamp)
        try insert.bind(6, parentIngestSequence)
        try insert.bind(7, itemIndex)
        try insert.bind(8, idempotencyKey)
        _ = try insert.stepRow()
        guard database.changes == 1 else { throw HandoffContractError.integrityConflict }
    }

    private func insertOrVerifyDeferredMarker(
        key: String,
        payloadToken: String,
        reason: String,
        allowInsert: Bool
    ) throws {
        let existing = try selectDeferred(key: key)
        if let existing {
            guard existing.payloadToken == payloadToken, existing.reason == reason,
                  existing.accountID == nil, existing.clientID == nil,
                  existing.parentIngestSequence == nil, existing.serverEventID == nil,
                  existing.rawEnvelopeSHA256 == nil, existing.rawEnvelopeFormatVersion == nil,
                  existing.rawEnvelope == nil else {
                throw HandoffContractError.integrityConflict
            }
            return
        }
        guard allowInsert else { throw HandoffContractError.integrityConflict }
        try insertDeferred(
            key: key,
            payloadToken: payloadToken,
            reason: reason,
            unit: nil,
            raw: nil
        )
    }

    private func insertOrVerifyRawDeferred(
        key: String,
        unit: ForegroundImportUnitV1,
        raw: RawImportV1,
        reason: String,
        allowInsert: Bool
    ) throws {
        guard sha256HexV1(raw.envelope) == unit.rawEnvelopeSHA256 else {
            throw HandoffContractError.integrityConflict
        }
        let existing = try selectDeferred(key: key)
        if let existing {
            guard existing.payloadToken == unit.parentRecordToken, existing.reason == reason,
                  existing.accountID == unit.scope.accountID, existing.clientID == unit.scope.clientID,
                  existing.parentIngestSequence == unit.parentIngestSequence,
                  existing.serverEventID == unit.parentServerEventID,
                  existing.rawEnvelopeSHA256 == unit.rawEnvelopeSHA256,
                  existing.rawEnvelopeFormatVersion == unit.rawEnvelopeFormatVersion,
                  existing.rawEnvelope == raw.envelope else {
                throw HandoffContractError.integrityConflict
            }
            return
        }
        guard allowInsert else { throw HandoffContractError.integrityConflict }
        try insertDeferred(
            key: key,
            payloadToken: unit.parentRecordToken,
            reason: reason,
            unit: unit,
            raw: raw
        )
    }

    private func selectDeferred(key: String) throws -> DeferredWorkRowV1? {
        let statement = try database.statement(
            "SELECT payload_token, reason, account_id, client_id, parent_ingest_sequence, server_event_id, " +
                "raw_envelope_sha256, raw_envelope_format_version, raw_envelope " +
                "FROM DeferredWork WHERE record_key = ?"
        )
        try statement.bind(1, key)
        guard try statement.stepRow() else { return nil }
        guard let payloadToken = statement.text(0), let reason = statement.text(1) else {
            throw HandoffContractError.corruptState
        }
        return DeferredWorkRowV1(
            payloadToken: payloadToken,
            reason: reason,
            accountID: statement.text(2),
            clientID: statement.text(3),
            parentIngestSequence: statement.optionalInt64(4),
            serverEventID: statement.text(5),
            rawEnvelopeSHA256: statement.text(6),
            rawEnvelopeFormatVersion: statement.optionalInt64(7),
            rawEnvelope: statement.data(8)
        )
    }

    private func insertDeferred(
        key: String,
        payloadToken: String,
        reason: String,
        unit: ForegroundImportUnitV1?,
        raw: RawImportV1?
    ) throws {
        let statement = try database.statement(
            "INSERT INTO DeferredWork(record_key, payload_token, reason, account_id, client_id, " +
                "parent_ingest_sequence, server_event_id, raw_envelope_sha256, " +
                "raw_envelope_format_version, raw_envelope) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
        )
        try statement.bind(1, key)
        try statement.bind(2, payloadToken)
        try statement.bind(3, reason)
        try statement.bind(4, unit?.scope.accountID)
        try statement.bind(5, unit?.scope.clientID)
        if let parentSequence = unit?.parentIngestSequence {
            try statement.bind(6, parentSequence)
        } else {
            try statement.bind(6, Optional<String>.none)
        }
        try statement.bind(7, unit?.parentServerEventID)
        try statement.bind(8, unit?.rawEnvelopeSHA256)
        if let formatVersion = unit?.rawEnvelopeFormatVersion {
            try statement.bind(9, formatVersion)
        } else {
            try statement.bind(9, Optional<String>.none)
        }
        try statement.bind(10, raw?.envelope)
        _ = try statement.stepRow()
        guard database.changes == 1 else { throw HandoffContractError.integrityConflict }
    }

    private func scalarCount(_ table: String) throws -> Int {
        let allowed = ["ImportLedger", "SyntheticMessage", "DeferredWork"]
        guard allowed.contains(table) else { throw HandoffContractError.corruptState }
        let statement = try database.statement("SELECT COUNT(*) FROM \(table)")
        guard try statement.stepRow() else { throw HandoffContractError.corruptState }
        return Int(statement.int64(0))
    }
}

private struct DeferredWorkRowV1 {
    let payloadToken: String
    let reason: String
    let accountID: String?
    let clientID: String?
    let parentIngestSequence: Int64?
    let serverEventID: String?
    let rawEnvelopeSHA256: String?
    let rawEnvelopeFormatVersion: Int64?
    let rawEnvelope: Data?
}

func childLedgerKey(_ child: ChildImportV1, scope: HandoffScopeV1) -> String {
    "child:v1:\(scope.accountID):\(scope.clientID):\(child.idempotencyKey)"
}

func rawLedgerKey(_ unit: ForegroundImportUnitV1) -> String {
    "raw:v1:\(unit.scope.accountID):\(unit.scope.clientID):\(unit.parentServerEventID)"
}
