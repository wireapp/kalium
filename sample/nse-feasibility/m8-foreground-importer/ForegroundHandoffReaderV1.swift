/* Direct-SQL native implementation of the versioned M6/M8 handoff contract. */

import Foundation
import SQLite3

private let sqliteTransientV1 = unsafeBitCast(-1, to: sqlite3_destructor_type.self)

final class SQLiteConnectionV1 {
    private var pointer: OpaquePointer?
    var handle: OpaquePointer {
        guard let pointer else { preconditionFailure("SQLite connection already closed") }
        return pointer
    }

    init(syntheticPlaintextPath: String, createIfMissing: Bool = false) throws {
        var pointer: OpaquePointer?
        let result = sqlite3_open_v2(
            syntheticPlaintextPath,
            &pointer,
            SQLITE_OPEN_READWRITE | SQLITE_OPEN_FULLMUTEX | (createIfMissing ? SQLITE_OPEN_CREATE : 0),
            nil
        )
        guard result == SQLITE_OK, let pointer else {
            if let pointer { sqlite3_close(pointer) }
            throw HandoffContractError.sqlite("open")
        }
        self.pointer = pointer
        try execute("PRAGMA foreign_keys = ON")
        try execute("PRAGMA busy_timeout = 0")
    }

    deinit { close() }

    func close() {
        guard let pointer else { return }
        sqlite3_close_v2(pointer)
        self.pointer = nil
    }

    func execute(_ sql: String) throws {
        guard sqlite3_exec(handle, sql, nil, nil, nil) == SQLITE_OK else {
            throw HandoffContractError.sqlite("exec")
        }
    }

    func statement(_ sql: String) throws -> SQLiteStatementV1 {
        try SQLiteStatementV1(database: handle, sql: sql)
    }

    var changes: Int { Int(sqlite3_changes(handle)) }
}

final class SQLiteStatementV1 {
    private let database: OpaquePointer
    let handle: OpaquePointer

    init(database: OpaquePointer, sql: String) throws {
        self.database = database
        var pointer: OpaquePointer?
        guard sqlite3_prepare_v2(database, sql, -1, &pointer, nil) == SQLITE_OK, let pointer else {
            throw HandoffContractError.sqlite("prepare")
        }
        handle = pointer
    }

    deinit { sqlite3_finalize(handle) }

    func bind(_ index: Int32, _ value: String?) throws {
        let result: Int32
        if let value {
            result = sqlite3_bind_text(handle, index, value, -1, sqliteTransientV1)
        } else {
            result = sqlite3_bind_null(handle, index)
        }
        guard result == SQLITE_OK else { throw HandoffContractError.sqlite("bind-text") }
    }

    func bind(_ index: Int32, _ value: Int64) throws {
        guard sqlite3_bind_int64(handle, index, value) == SQLITE_OK else {
            throw HandoffContractError.sqlite("bind-int")
        }
    }

    func bind(_ index: Int32, _ value: Data?) throws {
        let result: Int32
        if let value {
            result = value.withUnsafeBytes { bytes in
                sqlite3_bind_blob(handle, index, bytes.baseAddress, Int32(bytes.count), sqliteTransientV1)
            }
        } else {
            result = sqlite3_bind_null(handle, index)
        }
        guard result == SQLITE_OK else { throw HandoffContractError.sqlite("bind-blob") }
    }

    func stepRow() throws -> Bool {
        let result = sqlite3_step(handle)
        if result == SQLITE_ROW { return true }
        if result == SQLITE_DONE { return false }
        throw HandoffContractError.sqlite("step")
    }

    func text(_ index: Int32) -> String? {
        guard sqlite3_column_type(handle, index) != SQLITE_NULL,
              let pointer = sqlite3_column_text(handle, index) else { return nil }
        return String(cString: pointer)
    }

    func int64(_ index: Int32) -> Int64 { sqlite3_column_int64(handle, index) }

    func optionalInt64(_ index: Int32) -> Int64? {
        sqlite3_column_type(handle, index) == SQLITE_NULL ? nil : sqlite3_column_int64(handle, index)
    }

    func data(_ index: Int32) -> Data? {
        guard sqlite3_column_type(handle, index) != SQLITE_NULL else { return nil }
        let count = Int(sqlite3_column_bytes(handle, index))
        guard count >= 0 else { return nil }
        guard count > 0 else { return Data() }
        guard let pointer = sqlite3_column_blob(handle, index) else { return nil }
        return Data(bytes: pointer, count: count)
    }
}

final class ForegroundHandoffReaderV1 {
    private let database: SQLiteConnectionV1
    private let identity = UUID()
    private let scope: HandoffScopeV1

    init(
        securedConnection: SQLiteConnectionV1,
        scope: HandoffScopeV1,
        allowedStorageProfiles: Set<String>
    ) throws {
        database = securedConnection
        self.scope = scope
        let statement = try database.statement(
            "SELECT contract_version, raw_envelope_format_version, child_payload_format_version, " +
                "blob_storage_format FROM ContractMetadata WHERE singleton_id = 1"
        )
        guard try statement.stepRow(),
              statement.int64(0) == 1,
              statement.int64(1) == 1,
              statement.int64(2) == 1,
              statement.text(3).map(allowedStorageProfiles.contains) == true else {
            throw HandoffContractError.incompatibleSchema
        }
    }

    func close() { database.close() }

    func readNextSnapshot() throws -> ForegroundImportSnapshotV1? {
        try database.execute("BEGIN DEFERRED")
        do {
            guard let maximum = try snapshotMaximum() else {
                try database.execute("COMMIT")
                return nil
            }
            let candidates = try parentCandidates(maximum: maximum)
            guard let candidate = candidates.first else {
                try database.execute("COMMIT")
                return nil
            }
            let raw = try loadRaw(sequence: candidate.sequence)
            let children = try loadChildren(
                parentSequence: candidate.sequence,
                additionalBlobBytes: candidate.rawBlobSize
            )
            guard raw.importState == "PENDING", children.allSatisfy({ $0.importState == "PENDING" }) else {
                throw HandoffContractError.corruptState
            }
            guard children.isEmpty || raw.receiveState == "COMPLETED" else {
                throw HandoffContractError.corruptState
            }
            let unit = try makeUnit(raw: raw, children: children)
            let snapshot = ForegroundImportSnapshotV1(
                protocolVersion: foregroundImportProtocolVersionV1,
                snapshotMaxIngestSequence: maximum,
                unit: unit,
                hasMore: candidates.count > 1,
                snapshotToken: snapshotTokenV1(maxIngestSequence: maximum, unit: unit),
                readerIdentity: identity
            )
            try database.execute("COMMIT")
            return snapshot
        } catch {
            try? database.execute("ROLLBACK")
            throw error
        }
    }

    func markImported(
        snapshot: ForegroundImportSnapshotV1,
        rawDisposition: RawDispositionV1?
    ) throws -> HandoffMarkResultV1 {
        guard snapshot.protocolVersion == foregroundImportProtocolVersionV1,
              snapshot.readerIdentity == identity,
              snapshot.unit.scope == scope,
              snapshot.snapshotToken == snapshotTokenV1(
                maxIngestSequence: snapshot.snapshotMaxIngestSequence,
                unit: snapshot.unit
              ) else {
            throw HandoffContractError.integrityConflict
        }
        try validateDisposition(snapshot.unit.rawImport?.action, rawDisposition)
        try database.execute("BEGIN IMMEDIATE")
        do {
            let currentRaw = try loadRaw(sequence: snapshot.unit.parentIngestSequence)
            let currentChildren = try loadChildren(
                parentSequence: snapshot.unit.parentIngestSequence,
                additionalBlobBytes: currentRaw.envelope.count
            )
            guard rawIdentityMatches(currentRaw, snapshot.unit),
                  currentChildren.count == snapshot.unit.children.count,
                  zip(currentChildren, snapshot.unit.children).allSatisfy({ current, captured in
                      current.childSequence == captured.childSequence &&
                          current.recordToken == captured.recordToken
                  }) else {
                throw HandoffContractError.integrityConflict
            }

            let allPending = currentRaw.importState == "PENDING" &&
                currentChildren.allSatisfy { $0.importState == "PENDING" }
            let allImported = currentRaw.importState == "IMPORTED" &&
                currentChildren.allSatisfy { $0.importState == "IMPORTED" }
            if allImported {
                guard importedLifecycleMatches(currentRaw, snapshot.unit, rawDisposition) else {
                    throw HandoffContractError.integrityConflict
                }
                try database.execute("COMMIT")
                return .alreadyImported
            }
            let currentUnit = try makeUnit(raw: currentRaw, children: currentChildren)
            guard allPending,
                  currentUnit.parentRecordToken == snapshot.unit.parentRecordToken else {
                throw HandoffContractError.integrityConflict
            }

            for child in currentChildren {
                try markChild(child)
                guard database.changes == 1 else { throw HandoffContractError.integrityConflict }
            }
            if let rawDisposition {
                try markRawWithDisposition(currentRaw, disposition: rawDisposition)
            } else {
                try markRawOnly(currentRaw)
            }
            guard database.changes == 1 else { throw HandoffContractError.integrityConflict }
            try database.execute("COMMIT")
            return .marked(rawParents: 1, children: currentChildren.count)
        } catch {
            try? database.execute("ROLLBACK")
            throw error
        }
    }

    func cursorValue() throws -> String? {
        let statement = try database.statement(
            "SELECT cursor_value, source_ingest_sequence, source_server_event_id, updated_at_epoch_millis " +
                "FROM DurableCursor WHERE account_id = ? AND client_id = ?"
        )
        try statement.bind(1, scope.accountID)
        try statement.bind(2, scope.clientID)
        guard try statement.stepRow() else { return nil }
        guard let cursor = statement.text(0),
              let sourceEventID = statement.text(2),
              isValidStoredValueV1(cursor, maximumUTF8Bytes: 256),
              statement.int64(3) >= 0 else {
            throw HandoffContractError.corruptState
        }
        let source = try loadRaw(sequence: statement.int64(1))
        guard source.scope == scope,
              source.serverEventID == sourceEventID,
              !source.isTransient,
              source.cursor == cursor else {
            throw HandoffContractError.corruptState
        }
        return cursor
    }

    /** Synthetic fault fixture only: adds a newer transient row without touching DurableCursor. */
    func insertSyntheticLateRaw(eventID: String, bytes: Data, timestamp: Int64) throws {
        let statement = try database.statement(
            "INSERT INTO RawEvent(account_id, client_id, server_event_id, raw_envelope, " +
                "raw_envelope_sha256, raw_envelope_format_version, server_timestamp_epoch_millis, " +
                "is_transient, associated_cursor, delivery_source, received_at_epoch_millis) " +
                "VALUES(?, ?, ?, ?, ?, 1, ?, 1, NULL, 'SYNTHETIC_FEASIBILITY', ?)"
        )
        try statement.bind(1, scope.accountID)
        try statement.bind(2, scope.clientID)
        try statement.bind(3, eventID)
        try statement.bind(4, bytes)
        try statement.bind(5, sha256HexV1(bytes))
        try statement.bind(6, timestamp)
        try statement.bind(7, timestamp)
        guard try !statement.stepRow() else { throw HandoffContractError.corruptState }
    }

    private func snapshotMaximum() throws -> Int64? {
        let statement = try database.statement(
            "SELECT MAX(ingest_sequence) FROM RawEvent WHERE account_id = ? AND client_id = ?"
        )
        try statement.bind(1, scope.accountID)
        try statement.bind(2, scope.clientID)
        guard try statement.stepRow() else { return nil }
        return statement.optionalInt64(0)
    }

    private func parentCandidates(maximum: Int64) throws -> [ParentCandidateV1] {
        let statement = try database.statement(
            "SELECT ingest_sequence, length(raw_envelope) FROM RawEvent " +
                "WHERE account_id = ? AND client_id = ? " +
                "AND ingest_sequence <= ? AND (import_state = 'PENDING' OR EXISTS (" +
                "SELECT 1 FROM ReceiveChild WHERE ReceiveChild.parent_ingest_sequence = RawEvent.ingest_sequence " +
                "AND ReceiveChild.import_state = 'PENDING')) ORDER BY ingest_sequence ASC LIMIT 2"
        )
        try statement.bind(1, scope.accountID)
        try statement.bind(2, scope.clientID)
        try statement.bind(3, maximum)
        var values: [ParentCandidateV1] = []
        while try statement.stepRow() {
            let size = Int(statement.int64(1))
            guard size > 0, size <= 65_536 else { throw HandoffContractError.corruptState }
            values.append(ParentCandidateV1(sequence: statement.int64(0), rawBlobSize: size))
        }
        return values
    }

    private func loadRaw(sequence: Int64) throws -> RawRowV1 {
        let lengthStatement = try database.statement(
            "SELECT length(raw_envelope) FROM RawEvent WHERE account_id = ? AND client_id = ? " +
                "AND ingest_sequence = ?"
        )
        try lengthStatement.bind(1, scope.accountID)
        try lengthStatement.bind(2, scope.clientID)
        try lengthStatement.bind(3, sequence)
        guard try lengthStatement.stepRow(), lengthStatement.int64(0) > 0,
              lengthStatement.int64(0) <= 65_536 else {
            throw HandoffContractError.corruptState
        }
        let statement = try database.statement(
            "SELECT ingest_sequence, account_id, client_id, server_event_id, raw_envelope, " +
                "raw_envelope_sha256, raw_envelope_format_version, server_timestamp_epoch_millis, " +
                "is_transient, associated_cursor, delivery_source, received_at_epoch_millis, " +
                "foreground_recovery_required, recovery_reason, ingestion_state, receive_state, import_state " +
                "FROM RawEvent WHERE account_id = ? AND client_id = ? AND ingest_sequence = ?"
        )
        try statement.bind(1, scope.accountID)
        try statement.bind(2, scope.clientID)
        try statement.bind(3, sequence)
        guard try statement.stepRow(), let raw = statement.data(4), let sha = statement.text(5),
              let account = statement.text(1), let client = statement.text(2),
              let event = statement.text(3), let source = statement.text(10),
              let ingestion = statement.text(14), let receive = statement.text(15),
              let importState = statement.text(16), !raw.isEmpty, raw.count <= 65_536,
              statement.int64(0) > 0,
              account == scope.accountID, client == scope.clientID,
              isValidStoredValueV1(account, maximumUTF8Bytes: 256),
              isValidStoredValueV1(client, maximumUTF8Bytes: 256),
              isValidStoredValueV1(event, maximumUTF8Bytes: 256),
              sha256HexV1(raw) == sha,
              statement.int64(6) == 1,
              statement.optionalInt64(7).map({ $0 >= 0 }) ?? true,
              statement.int64(8) == 0 || statement.int64(8) == 1,
              statement.int64(8) != 0 || statement.text(9) != nil,
              statement.text(9).map({ isValidStoredValueV1($0, maximumUTF8Bytes: 256) }) ?? true,
              ["CONSUMABLE_WEBSOCKET", "PENDING_PAGE", "FOREGROUND_SYNC", "SYNTHETIC_FEASIBILITY"]
                .contains(source),
              statement.int64(11) >= 0,
              statement.int64(12) == 0 || statement.int64(12) == 1,
              statement.text(13).map({ isValidStoredValueV1($0, maximumUTF8Bytes: 256) }) ?? true,
              ingestion == "RAW_STORED",
              ["PENDING", "COMPLETED", "DEFERRED_TO_APP"].contains(receive),
              ["PENDING", "IMPORTED"].contains(importState) else {
            throw HandoffContractError.corruptState
        }
        return RawRowV1(
            ingestSequence: statement.int64(0),
            scope: HandoffScopeV1(accountID: account, clientID: client),
            serverEventID: event,
            envelope: raw,
            envelopeSHA256: sha,
            formatVersion: statement.int64(6),
            serverTimestampMillis: statement.optionalInt64(7),
            isTransient: statement.int64(8) != 0,
            cursor: statement.text(9),
            deliverySource: source,
            receivedAtMillis: statement.int64(11),
            recoveryRequired: statement.int64(12) != 0,
            recoveryReason: statement.text(13),
            receiveState: receive,
            importState: importState
        )
    }

    private func loadChildren(parentSequence: Int64, additionalBlobBytes: Int) throws -> [ChildImportV1] {
        let metadata = try loadChildMetadata(parentSequence: parentSequence)
        guard additionalBlobBytes >= 0,
              metadata.reduce(additionalBlobBytes, { $0 + $1.blobSize }) <= 262_144 else {
            throw HandoffContractError.corruptState
        }
        let statement = try database.statement(
            "SELECT ReceiveChild.child_sequence, ReceiveChild.parent_ingest_sequence, RawEvent.server_event_id, " +
                "ReceiveChild.item_index, ReceiveChild.idempotency_key, ReceiveChild.conversation_id, " +
                "ReceiveChild.sender_id, ReceiveChild.sender_client_id, ReceiveChild.protocol, " +
                "ReceiveChild.message_timestamp_epoch_millis, ReceiveChild.decrypted_proto, " +
                "ReceiveChild.decrypted_proto_sha256, ReceiveChild.crypto_state_applied, " +
                "ReceiveChild.receive_classification, ReceiveChild.failure_classification, " +
                "ReceiveChild.decryption_state, ReceiveChild.notification_state, ReceiveChild.import_state, " +
                "ReceiveChild.retry_count, ReceiveChild.account_id, ReceiveChild.client_id " +
                "FROM ReceiveChild JOIN RawEvent ON RawEvent.ingest_sequence = " +
                "ReceiveChild.parent_ingest_sequence WHERE ReceiveChild.parent_ingest_sequence = ? " +
                "ORDER BY ReceiveChild.item_index ASC, ReceiveChild.idempotency_key ASC"
        )
        try statement.bind(1, parentSequence)
        var children: [ChildImportV1] = []
        while try statement.stepRow() {
            guard children.count < 8,
                  let parentEvent = statement.text(2),
                  let key = statement.text(4),
                  let receiveProtocol = statement.text(8),
                  let classification = statement.text(13),
                  let decryption = statement.text(15),
                  let notification = statement.text(16),
                  let importState = statement.text(17),
                  statement.text(19) == scope.accountID,
                  statement.text(20) == scope.clientID,
                  statement.int64(0) > 0,
                  statement.int64(1) == parentSequence,
                  statement.int64(3) >= 0, statement.int64(3) < 8,
                  isValidStoredValueV1(parentEvent, maximumUTF8Bytes: 256),
                  isValidStoredValueV1(key, maximumUTF8Bytes: 256),
                  isKnownIdempotencyKeyV1(key, parentEvent: parentEvent, itemIndex: statement.int64(3)),
                  statement.text(5).map({ isValidStoredValueV1($0, maximumUTF8Bytes: 256) }) ?? true,
                  statement.text(6).map({ isValidStoredValueV1($0, maximumUTF8Bytes: 256) }) ?? true,
                  statement.text(7).map({ isValidStoredValueV1($0, maximumUTF8Bytes: 256) }) ?? true,
                  ["PROTEUS", "MLS", "NONE"].contains(receiveProtocol),
                  statement.optionalInt64(9).map({ $0 >= 0 }) ?? true,
                  ["APPLICATION_MESSAGE", "HANDSHAKE_ONLY", "WELCOME", "UNSUPPORTED", "NON_MESSAGE_EVENT"]
                    .contains(classification),
                  statement.text(14).map({ isValidStoredValueV1($0, maximumUTF8Bytes: 256) }) ?? true,
                  ["NOT_REQUIRED", "DECRYPTED", "HANDSHAKE_APPLIED", "FAILED_TERMINAL"]
                    .contains(decryption),
                  ["NOT_ELIGIBLE", "PENDING", "PRESENTED", "SUPPRESSED", "FAILED"].contains(notification),
                  ["PENDING", "IMPORTED"].contains(importState),
                  statement.int64(12) == 0 || statement.int64(12) == 1,
                  statement.int64(18) >= 0, statement.int64(18) <= 3 else {
                throw HandoffContractError.corruptState
            }
            let proto = statement.data(10)
            let protoSHA = statement.text(11)
            let cryptoApplied = statement.int64(12) != 0
            guard proto.map(sha256HexV1) == protoSHA,
                  (proto?.count ?? 0) <= 65_536,
                  !(classification == "APPLICATION_MESSAGE" &&
                    (proto == nil || decryption != "DECRYPTED" || !cryptoApplied)),
                  !(decryption == "DECRYPTED" && (proto == nil || !cryptoApplied)),
                  !(decryption == "HANDSHAKE_APPLIED" && !cryptoApplied),
                  !(decryption == "FAILED_TERMINAL" && statement.text(14) == nil) else {
                throw HandoffContractError.corruptState
            }
            var action = try childActionV1(
                classification: classification,
                decryptionState: decryption,
                cryptoStateApplied: cryptoApplied,
                hasDecryptedProto: proto != nil
            )
            if classification == "APPLICATION_MESSAGE" &&
                (statement.text(5) == nil || statement.optionalInt64(9) == nil) {
                action = .scheduleForegroundRecovery
            }
            let provisional = ChildImportV1(
                childSequence: statement.int64(0),
                parentIngestSequence: statement.int64(1),
                parentServerEventID: parentEvent,
                itemIndex: statement.int64(3),
                idempotencyKey: key,
                conversationID: statement.text(5),
                senderID: statement.text(6),
                senderClientID: statement.text(7),
                receiveProtocol: receiveProtocol,
                messageTimestampMillis: statement.optionalInt64(9),
                decryptedProto: proto,
                decryptedProtoSHA256: protoSHA,
                cryptoStateApplied: cryptoApplied,
                receiveClassification: classification,
                failureClassification: statement.text(14),
                decryptionState: decryption,
                notificationState: notification,
                importState: importState,
                retryCount: statement.int64(18),
                action: action,
                recordToken: ""
            )
            let token = childRecordTokenV1(provisional, scope: scope)
            children.append(ChildImportV1(
                childSequence: provisional.childSequence,
                parentIngestSequence: provisional.parentIngestSequence,
                parentServerEventID: provisional.parentServerEventID,
                itemIndex: provisional.itemIndex,
                idempotencyKey: provisional.idempotencyKey,
                conversationID: provisional.conversationID,
                senderID: provisional.senderID,
                senderClientID: provisional.senderClientID,
                receiveProtocol: provisional.receiveProtocol,
                messageTimestampMillis: provisional.messageTimestampMillis,
                decryptedProto: provisional.decryptedProto,
                decryptedProtoSHA256: provisional.decryptedProtoSHA256,
                cryptoStateApplied: provisional.cryptoStateApplied,
                receiveClassification: provisional.receiveClassification,
                failureClassification: provisional.failureClassification,
                decryptionState: provisional.decryptionState,
                notificationState: provisional.notificationState,
                importState: provisional.importState,
                retryCount: provisional.retryCount,
                action: action,
                recordToken: token
            ))
        }
        guard children.enumerated().allSatisfy({ $0.offset == $0.element.itemIndex }) else {
            throw HandoffContractError.corruptState
        }
        guard children.count == metadata.count,
              zip(children, metadata).allSatisfy({ child, row in
                  child.childSequence == row.childSequence && child.itemIndex == row.itemIndex &&
                      child.idempotencyKey == row.idempotencyKey &&
                      (child.decryptedProto?.count ?? 0) == row.blobSize
              }) else {
            throw HandoffContractError.corruptState
        }
        return children
    }

    private func loadChildMetadata(parentSequence: Int64) throws -> [ChildMetadataV1] {
        let statement = try database.statement(
            "SELECT child_sequence, item_index, idempotency_key, length(decrypted_proto) FROM ReceiveChild " +
                "WHERE parent_ingest_sequence = ? ORDER BY item_index ASC, idempotency_key ASC LIMIT 9"
        )
        try statement.bind(1, parentSequence)
        var rows: [ChildMetadataV1] = []
        var totalBytes = 0
        while try statement.stepRow() {
            guard rows.count < 8, let key = statement.text(2) else {
                throw HandoffContractError.corruptState
            }
            let blobSize = Int(statement.optionalInt64(3) ?? 0)
            guard blobSize >= 0, blobSize <= 65_536 else { throw HandoffContractError.corruptState }
            totalBytes += blobSize
            guard totalBytes <= 262_144 else { throw HandoffContractError.corruptState }
            rows.append(
                ChildMetadataV1(
                    childSequence: statement.int64(0),
                    itemIndex: statement.int64(1),
                    idempotencyKey: key,
                    blobSize: blobSize
                )
            )
        }
        return rows
    }

    private func makeUnit(raw: RawRowV1, children: [ChildImportV1]) throws -> ForegroundImportUnitV1 {
        guard raw.scope == scope, raw.formatVersion == 1,
              children.allSatisfy({
                  $0.parentIngestSequence == raw.ingestSequence && $0.parentServerEventID == raw.serverEventID
              }) else {
            throw HandoffContractError.corruptState
        }
        let childRequiresRecovery = children.contains { $0.action == .scheduleForegroundRecovery }
        let rawRequired = raw.receiveState == "DEFERRED_TO_APP" || raw.recoveryRequired ||
            children.isEmpty || childRequiresRecovery
        let rawAction: ForegroundImportActionV1? = rawRequired ? (
            raw.receiveState == "PENDING" || raw.receiveState == "DEFERRED_TO_APP" ||
                raw.recoveryRequired || childRequiresRecovery
                ? .scheduleForegroundRecovery : .recordCompletion
        ) : nil
        let provisional = ForegroundImportUnitV1(
            scope: scope,
            parentIngestSequence: raw.ingestSequence,
            parentServerEventID: raw.serverEventID,
            rawEnvelopeSHA256: raw.envelopeSHA256,
            rawEnvelopeFormatVersion: raw.formatVersion,
            serverTimestampMillis: raw.serverTimestampMillis,
            isTransient: raw.isTransient,
            associatedCursor: raw.cursor,
            deliverySource: raw.deliverySource,
            receivedAtMillis: raw.receivedAtMillis,
            receiveState: raw.receiveState,
            foregroundRecoveryRequired: raw.recoveryRequired,
            recoveryReason: raw.recoveryReason,
            importState: raw.importState,
            rawImport: rawAction.map { RawImportV1(envelope: raw.envelope, action: $0) },
            children: children,
            parentRecordToken: ""
        )
        return ForegroundImportUnitV1(
            scope: provisional.scope,
            parentIngestSequence: provisional.parentIngestSequence,
            parentServerEventID: provisional.parentServerEventID,
            rawEnvelopeSHA256: provisional.rawEnvelopeSHA256,
            rawEnvelopeFormatVersion: provisional.rawEnvelopeFormatVersion,
            serverTimestampMillis: provisional.serverTimestampMillis,
            isTransient: provisional.isTransient,
            associatedCursor: provisional.associatedCursor,
            deliverySource: provisional.deliverySource,
            receivedAtMillis: provisional.receivedAtMillis,
            receiveState: provisional.receiveState,
            foregroundRecoveryRequired: provisional.foregroundRecoveryRequired,
            recoveryReason: provisional.recoveryReason,
            importState: provisional.importState,
            rawImport: provisional.rawImport,
            children: provisional.children,
            parentRecordToken: parentRecordTokenV1(provisional)
        )
    }

    private func markChild(_ child: ChildImportV1) throws {
        let statement = try database.statement(
            "UPDATE ReceiveChild SET import_state = 'IMPORTED' WHERE account_id = ? AND client_id = ? " +
                "AND child_sequence = ? AND parent_ingest_sequence = ? AND item_index = ? " +
                "AND idempotency_key = ? AND decrypted_proto_sha256 IS ? AND import_state = 'PENDING'"
        )
        try statement.bind(1, scope.accountID)
        try statement.bind(2, scope.clientID)
        try statement.bind(3, child.childSequence)
        try statement.bind(4, child.parentIngestSequence)
        try statement.bind(5, child.itemIndex)
        try statement.bind(6, child.idempotencyKey)
        try statement.bind(7, child.decryptedProtoSHA256)
        _ = try statement.stepRow()
    }

    private func markRawOnly(_ raw: RawRowV1) throws {
        let statement = try database.statement(
            "UPDATE RawEvent SET import_state = 'IMPORTED' WHERE account_id = ? AND client_id = ? " +
                "AND ingest_sequence = ? AND server_event_id = ? AND raw_envelope_sha256 = ? " +
                "AND import_state = 'PENDING'"
        )
        try bindRawIdentity(statement, raw)
        _ = try statement.stepRow()
    }

    private func markRawWithDisposition(_ raw: RawRowV1, disposition: RawDispositionV1) throws {
        let nextState: String
        let nextRecovery: Int64
        let nextReason: String?
        switch disposition {
        case .processedByForeground:
            nextState = "COMPLETED"
            nextRecovery = raw.recoveryRequired ? 1 : 0
            nextReason = raw.recoveryReason
        case .durablyQueuedForForeground:
            nextState = "DEFERRED_TO_APP"
            nextRecovery = 1
            nextReason = raw.recoveryReason ?? foregroundQueuedReasonV1
        }
        let statement = try database.statement(
            "UPDATE RawEvent SET receive_state = ?, foreground_recovery_required = ?, recovery_reason = ?, " +
                "receive_claim_token = NULL, receive_claimed_at_epoch_millis = NULL, import_state = 'IMPORTED' " +
                "WHERE account_id = ? AND client_id = ? AND ingest_sequence = ? AND server_event_id = ? " +
                "AND raw_envelope_sha256 = ? AND receive_state = ? AND foreground_recovery_required = ? " +
                "AND recovery_reason IS ? AND import_state = 'PENDING'"
        )
        try statement.bind(1, nextState)
        try statement.bind(2, nextRecovery)
        try statement.bind(3, nextReason)
        try statement.bind(4, scope.accountID)
        try statement.bind(5, scope.clientID)
        try statement.bind(6, raw.ingestSequence)
        try statement.bind(7, raw.serverEventID)
        try statement.bind(8, raw.envelopeSHA256)
        try statement.bind(9, raw.receiveState)
        try statement.bind(10, raw.recoveryRequired ? 1 : 0)
        try statement.bind(11, raw.recoveryReason)
        _ = try statement.stepRow()
    }

    private func bindRawIdentity(_ statement: SQLiteStatementV1, _ raw: RawRowV1) throws {
        try statement.bind(1, scope.accountID)
        try statement.bind(2, scope.clientID)
        try statement.bind(3, raw.ingestSequence)
        try statement.bind(4, raw.serverEventID)
        try statement.bind(5, raw.envelopeSHA256)
    }

    private func validateDisposition(
        _ action: ForegroundImportActionV1?,
        _ disposition: RawDispositionV1?
    ) throws {
        switch (action, disposition) {
        case (nil, nil), (.recordCompletion?, .processedByForeground?),
             (.scheduleForegroundRecovery?, .durablyQueuedForForeground?): return
        default: throw HandoffContractError.invalidDisposition
        }
    }

    private func rawIdentityMatches(_ raw: RawRowV1, _ unit: ForegroundImportUnitV1) -> Bool {
        raw.scope == unit.scope && raw.ingestSequence == unit.parentIngestSequence &&
            raw.serverEventID == unit.parentServerEventID && raw.envelopeSHA256 == unit.rawEnvelopeSHA256 &&
            raw.formatVersion == unit.rawEnvelopeFormatVersion &&
            raw.serverTimestampMillis == unit.serverTimestampMillis && raw.isTransient == unit.isTransient &&
            raw.cursor == unit.associatedCursor && raw.deliverySource == unit.deliverySource &&
            raw.receivedAtMillis == unit.receivedAtMillis
    }

    private func importedLifecycleMatches(
        _ raw: RawRowV1,
        _ unit: ForegroundImportUnitV1,
        _ disposition: RawDispositionV1?
    ) -> Bool {
        guard let disposition else {
            return raw.receiveState == unit.receiveState && raw.recoveryRequired == unit.foregroundRecoveryRequired &&
                raw.recoveryReason == unit.recoveryReason
        }
        switch disposition {
        case .processedByForeground:
            return raw.receiveState == "COMPLETED" && raw.recoveryRequired == unit.foregroundRecoveryRequired &&
                raw.recoveryReason == unit.recoveryReason
        case .durablyQueuedForForeground:
            return raw.receiveState == "DEFERRED_TO_APP" && raw.recoveryRequired &&
                raw.recoveryReason == (unit.recoveryReason ?? foregroundQueuedReasonV1)
        }
    }
}

private struct RawRowV1 {
    let ingestSequence: Int64
    let scope: HandoffScopeV1
    let serverEventID: String
    let envelope: Data
    let envelopeSHA256: String
    let formatVersion: Int64
    let serverTimestampMillis: Int64?
    let isTransient: Bool
    let cursor: String?
    let deliverySource: String
    let receivedAtMillis: Int64
    let recoveryRequired: Bool
    let recoveryReason: String?
    let receiveState: String
    let importState: String

}

private struct ParentCandidateV1 {
    let sequence: Int64
    let rawBlobSize: Int
}

private struct ChildMetadataV1 {
    let childSequence: Int64
    let itemIndex: Int64
    let idempotencyKey: String
    let blobSize: Int
}

private func isValidStoredValueV1(_ value: String, maximumUTF8Bytes: Int) -> Bool {
    !value.isEmpty && !value.contains("\0") && value.lengthOfBytes(using: .utf8) <= maximumUTF8Bytes
}

private func isKnownIdempotencyKeyV1(_ value: String, parentEvent: String, itemIndex: Int64) -> Bool {
    let messagePrefix = "message-uid:v1:"
    if value.hasPrefix(messagePrefix) { return value.count > messagePrefix.count }
    if value.hasPrefix("fallback-v1:") {
        return value == fallbackChildIdempotencyKeyV1(serverEventID: parentEvent, itemIndex: itemIndex)
    }
    return false
}
