/* Standalone macOS proof using the actual M6 handoff DB and a synthetic native main DB. */

import Foundation

func requireV1(_ condition: Bool) throws {
    if !condition { throw HandoffContractError.corruptState }
}

func runForegroundImporterProbeV1(
    sharedRoot: String,
    handoffPath: String,
    mainPath: String,
    expectedKotlinSnapshotToken: String
) throws -> String {
    let scope = HandoffScopeV1(accountID: syntheticInboxAccountV1, clientID: syntheticInboxClientV1)
    try requireV1(
        appleAccountLockDigestV1(
            scope: HandoffScopeV1(accountID: "probe-account", clientID: "probe-client")
        ) == "9c03173842651044f0848cfb08e7ef905916c4eae2d198cb7ab691d9124ee5ba"
    )
    let attempt = try tryAcquireDisposableExistingAppleAccountSyncLockV1(sharedRoot: sharedRoot, scope: scope)
    guard case .acquired(let lease) = attempt else { throw HandoffContractError.lockUnavailable }
    var lockHeld = true
    defer {
        lockHeld = false
        lease.release()
    }

    let contender = try tryAcquireDisposableExistingAppleAccountSyncLockV1(sharedRoot: sharedRoot, scope: scope)
    guard case .unavailable = contender else { throw HandoffContractError.corruptState }

    let handoffConnection = try SQLiteConnectionV1(syntheticPlaintextPath: handoffPath)
    let reader = try ForegroundHandoffReaderV1(
        securedConnection: handoffConnection,
        scope: scope,
        allowedStorageProfiles: ["PLAINTEXT_SYNTHETIC_SPIKE_V1"]
    )
    let mainStore = try SyntheticNativeMainStoreV1(path: mainPath)
    defer {
        mainStore.close()
        reader.close()
    }
    let coordinator = ForegroundImportCoordinatorV1(
        reader: reader,
        mainStore: mainStore,
        lockIsHeld: { lockHeld }
    )

    let cursorBeforeImport = try reader.cursorValue()
    let initial = try reader.readNextSnapshot()
    guard let initial else { throw HandoffContractError.corruptState }
    try requireV1(initial.snapshotToken == expectedKotlinSnapshotToken)
    try requireV1(initial.unit.children.map(\.itemIndex) == [0, 1])
    try requireV1(initial.unit.children.allSatisfy { $0.action == .upsertApplicationMessage })

    do {
        _ = try mainStore.importAtomically(snapshot: initial, injectFailureBeforeCommit: true)
        throw HandoffContractError.corruptState
    } catch HandoffContractError.injectedMainFailure {
        try requireV1(mainStore.ledgerCount() == 0 && mainStore.messageCount() == 0)
    }
    try requireV1(try reader.readNextSnapshot()?.snapshotToken == initial.snapshotToken)

    let firstMainCommit = try mainStore.importAtomically(snapshot: initial, injectFailureBeforeCommit: false)
    try requireV1(!firstMainCommit.exactReplay && mainStore.messageCount() == 2)
    try requireV1(try reader.readNextSnapshot()?.snapshotToken == initial.snapshotToken)

    let replayCommit = try mainStore.importAtomically(snapshot: initial, injectFailureBeforeCommit: false)
    try requireV1(replayCommit.exactReplay && mainStore.messageCount() == 2)
    let lateBytes = Data("{\"type\":\"synthetic-native-late\"}".utf8)
    try reader.insertSyntheticLateRaw(eventID: "synthetic-native-late", bytes: lateBytes, timestamp: 20)
    try requireV1(try reader.cursorValue() == cursorBeforeImport)
    guard case .marked(rawParents: 1, children: 2) = try reader.markImported(
        snapshot: initial,
        rawDisposition: nil
    ) else {
        throw HandoffContractError.corruptState
    }
    guard case .alreadyImported = try reader.markImported(snapshot: initial, rawDisposition: nil) else {
        throw HandoffContractError.corruptState
    }

    let next = try reader.readNextSnapshot()
    try requireV1(next?.unit.parentServerEventID == "synthetic-m8-event-handshake")
    let firstChildKey = childLedgerKey(initial.unit.children[0], scope: scope)
    try mainStore.proveConflict(recordKey: firstChildKey)

    var parentOrder: [Int64] = []
    while let snapshot = try reader.readNextSnapshot() {
        parentOrder.append(snapshot.unit.parentIngestSequence)
        let result = try coordinator.importNext()
        guard case .committedAndMarked = result else { throw HandoffContractError.corruptState }
    }
    try requireV1(parentOrder == parentOrder.sorted() && parentOrder.count == 3)
    try requireV1(mainStore.messageCount() == 2)
    try requireV1(mainStore.ledgerCount() == 5)
    try requireV1(mainStore.deferredCount() == 2)
    try requireV1(mainStore.deferredRawPayloadCount() == 2)
    try requireV1(mainStore.messageIDsInUIOrder() == ["synthetic-message-1", "synthetic-message-2"])
    try requireV1(try reader.cursorValue() == cursorBeforeImport)

    var transitionedCursor: String?
    try coordinator.transitionToForegroundSync { cursor in
        try requireV1(lockHeld)
        transitionedCursor = cursor
    }
    try requireV1(transitionedCursor == cursorBeforeImport)
    let finalContender = try tryAcquireDisposableExistingAppleAccountSyncLockV1(
        sharedRoot: sharedRoot,
        scope: scope
    )
    guard case .unavailable = finalContender else { throw HandoffContractError.corruptState }

    mainStore.close()
    reader.close()
    try requireV1(lockHeld)
    return "nativeSwift=true; noKaliumFramework=true; mainFailurePending=true; " +
        "postMainCrashReplay=true; mainRowsStable=true; exactDuplicate=true; conflict=true; " +
        "newRowsExcluded=true; parentOrder=true; protocolTimestampOrder=true; " +
        "rawQueuedNoReplay=true; rawDurablyOwned=true; cursorUnchangedByImport=true; transitionUnderLock=true; " +
        "kotlinSwiftTokenParity=true; databasesClosedBeforeUnlock=true; plaintextSyntheticOnly=true"
}

@main
struct ForegroundImporterProbeMainV1 {
    static func main() {
        guard CommandLine.arguments.count == 5 else {
            fputs(
                "usage: ForegroundImporterProbe <shared-root> <handoff-db> <synthetic-main-db> <kotlin-token>\n",
                stderr
            )
            exit(64)
        }
        do {
            let detail = try runForegroundImporterProbeV1(
                sharedRoot: CommandLine.arguments[1],
                handoffPath: CommandLine.arguments[2],
                mainPath: CommandLine.arguments[3],
                expectedKotlinSnapshotToken: CommandLine.arguments[4]
            )
            print("gate=native-foreground-importer passed=true detail=\(detail)")
        } catch {
            print("gate=native-foreground-importer passed=false error=\(error)")
            exit(1)
        }
    }
}
