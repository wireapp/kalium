/*
 * Disposable host for ADR 0010's AVS/CoreCrypto dynamic-framework isolation experiment.
 * It intentionally invokes AVS before and after CoreCrypto in the same process.
 */

import Foundation
import KaliumNseAvsIsolation
import KaliumNseFeasibility

guard CommandLine.arguments.count == 3 else {
    fputs("usage: kalium-nse-split-framework-host <absolute-shared-root> <avs-first|core-first>\n", stderr)
    exit(64)
}

let sharedRoot = CommandLine.arguments[1]
let order = CommandLine.arguments[2]
let avsProbe = NotificationAvsIsolationProbe()

func runCoreCrypto() -> Bool {
    let completion = DispatchSemaphore(value: 0)
    var passed = false
    var detail = "completion was not called"

    NseFeasibilityProbe().probeSequentialCoreCryptoOpenClose(sharedRoot: sharedRoot) { result, error in
        if let error {
            detail = "error=\(error)"
        } else if let result {
            passed = result.passed
            detail = result.detail
        }
        completion.signal()
    }

    guard completion.wait(timeout: .now() + 30) == .success else {
        print("gate=split-framework-corecrypto passed=false detail=timed out")
        return false
    }

    print("gate=split-framework-corecrypto passed=\(passed) detail=\(detail)")
    return passed
}

switch order {
case "avs-first":
    print("gate=split-framework-avs-before passed=true detail=\(avsProbe.run())")
    guard runCoreCrypto() else { exit(1) }
    print("gate=split-framework-avs-after passed=true detail=\(avsProbe.run())")
case "core-first":
    guard runCoreCrypto() else { exit(1) }
    print("gate=split-framework-avs-middle passed=true detail=\(avsProbe.run())")
    guard runCoreCrypto() else { exit(1) }
default:
    fputs("unknown order: \(order)\n", stderr)
    exit(1)
}

print("gate=split-framework-coexistence passed=true detail=AVS and CoreCrypto completed in one process order=\(order)")
