/* Transaction ordering reference: main commit first, exact handoff CAS second. */

import Foundation

enum ForegroundImportRunResultV1 {
    case noPending
    case committedAndMarked(snapshotToken: String, exactMainReplay: Bool)
    case committedCrashEquivalent(snapshotToken: String)
}

final class ForegroundImportCoordinatorV1 {
    private let reader: ForegroundHandoffReaderV1
    private let mainStore: ForegroundMainStoreV1
    private let lockIsHeld: () -> Bool

    init(
        reader: ForegroundHandoffReaderV1,
        mainStore: ForegroundMainStoreV1,
        lockIsHeld: @escaping () -> Bool
    ) {
        self.reader = reader
        self.mainStore = mainStore
        self.lockIsHeld = lockIsHeld
    }

    func importNext(
        injectMainFailure: Bool = false,
        simulateCrashAfterMainCommit: Bool = false
    ) throws -> ForegroundImportRunResultV1 {
        guard lockIsHeld() else { throw HandoffContractError.lockUnavailable }
        guard let snapshot = try reader.readNextSnapshot() else { return .noPending }
        guard lockIsHeld() else { throw HandoffContractError.lockUnavailable }
        let receipt = try mainStore.importAtomically(
            snapshot: snapshot,
            injectFailureBeforeCommit: injectMainFailure
        )
        guard receipt.snapshotToken == snapshot.snapshotToken else {
            throw HandoffContractError.integrityConflict
        }
        if simulateCrashAfterMainCommit {
            return .committedCrashEquivalent(snapshotToken: snapshot.snapshotToken)
        }
        guard lockIsHeld() else { throw HandoffContractError.lockUnavailable }
        let disposition: RawDispositionV1?
        switch snapshot.unit.rawImport?.action {
        case nil: disposition = nil
        case .some(.recordCompletion): disposition = .processedByForeground
        case .some(.scheduleForegroundRecovery): disposition = .durablyQueuedForForeground
        default: throw HandoffContractError.invalidDisposition
        }
        _ = try reader.markImported(snapshot: snapshot, rawDisposition: disposition)
        return .committedAndMarked(
            snapshotToken: snapshot.snapshotToken,
            exactMainReplay: receipt.exactReplay
        )
    }

    /** Cursor is read for foreground sync handoff while the same lock remains owned. */
    func transitionToForegroundSync(_ transition: (String?) throws -> Void) throws {
        guard lockIsHeld() else { throw HandoffContractError.lockUnavailable }
        try transition(reader.cursorValue())
        guard lockIsHeld() else { throw HandoffContractError.lockUnavailable }
    }
}
