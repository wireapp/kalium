/* Cross-language token parity probe for ADR 0010 Milestone 9. */

import Foundation

@main
struct M9LifecycleTokenProbe {
    static func main() {
        guard CommandLine.arguments.count == 3 else {
            fputs("usage: M9LifecycleTokenProbe <expected-recovery-token> <expected-tombstone-token>\n", stderr)
            exit(64)
        }
        let scope = HandoffScopeV1(
            accountID: syntheticInboxAccountV1,
            clientID: syntheticInboxClientV1
        )
        let recovery = globalRecoveryTokenV1(
            scope: scope,
            reason: "SYNTHETIC_RECOVERY",
            recordedAtMillis: 2
        )
        let tombstone = accountTombstoneTokenV1(
            scope: scope,
            removalID: "removal-v1",
            reason: "ACCOUNT_REMOVED",
            tombstonedAtMillis: 10
        )
        guard notificationInboxContractVersionV2 == 2,
              recovery == CommandLine.arguments[1],
              tombstone == CommandLine.arguments[2] else {
            fputs("gate=m9-lifecycle-token-parity passed=false\n", stderr)
            exit(1)
        }
        print(
            "gate=m9-lifecycle-token-parity passed=true contractV2=true " +
                "recoveryTokenParity=true tombstoneTokenParity=true"
        )
    }
}
