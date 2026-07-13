/*
 * Disposable digest/flock parity reference for Milestone 5.
 * This helper validates only the final entry. It does not perform M5's descriptor-relative,
 * no-follow ancestor walk and must never construct a production App Group lock path.
 */

import CryptoKit
import Darwin
import Foundation

final class AppleAccountSyncLockLeaseV1 {
    private var descriptor: Int32

    init(descriptor: Int32) {
        self.descriptor = descriptor
    }

    deinit { release() }

    func release() {
        guard descriptor >= 0 else { return }
        _ = flock(descriptor, LOCK_UN)
        _ = close(descriptor)
        descriptor = -1
    }
}

enum AppleAccountSyncLockAttemptV1 {
    case acquired(AppleAccountSyncLockLeaseV1)
    case unavailable
}

func tryAcquireDisposableExistingAppleAccountSyncLockV1(
    sharedRoot: String,
    scope: HandoffScopeV1
) throws -> AppleAccountSyncLockAttemptV1 {
    let digest = appleAccountLockDigestV1(scope: scope)
    let path = URL(fileURLWithPath: sharedRoot, isDirectory: true)
        .appendingPathComponent("kalium-nse", isDirectory: true)
        .appendingPathComponent("v1", isDirectory: true)
        .appendingPathComponent(digest, isDirectory: true)
        .appendingPathComponent("sync.lock", isDirectory: false)
        .path
    let descriptor = open(path, O_RDWR | O_NOFOLLOW | O_CLOEXEC)
    guard descriptor >= 0 else { throw HandoffContractError.corruptState }
    do {
        var status = stat()
        guard fstat(descriptor, &status) == 0,
              (status.st_mode & S_IFMT) == S_IFREG,
              status.st_uid == geteuid(),
              (status.st_mode & 0o777) == 0o600,
              status.st_nlink == 1 else {
            throw HandoffContractError.corruptState
        }
        if flock(descriptor, LOCK_EX | LOCK_NB) == 0 {
            return .acquired(AppleAccountSyncLockLeaseV1(descriptor: descriptor))
        }
        if errno == EWOULDBLOCK || errno == EAGAIN {
            _ = close(descriptor)
            return .unavailable
        }
        throw HandoffContractError.corruptState
    } catch {
        _ = close(descriptor)
        throw error
    }
}

func appleAccountLockDigestV1(scope: HandoffScopeV1) -> String {
    var data = Data("com.wire.kalium.notification-sync.lock/v1".utf8)
    appendLengthPrefixedLockFieldV1(scope.accountID, to: &data)
    appendLengthPrefixedLockFieldV1(scope.clientID, to: &data)
    return SHA256.hash(data: data).map { String(format: "%02x", $0) }.joined()
}

private func appendLengthPrefixedLockFieldV1(_ value: String, to data: inout Data) {
    let bytes = Data(value.utf8)
    var size = UInt32(bytes.count).bigEndian
    withUnsafeBytes(of: &size) { data.append(contentsOf: $0) }
    data.append(bytes)
}
