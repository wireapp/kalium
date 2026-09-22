# NSE rebuild plan status

The incremental rebuild has landed its prepared-account runtime, shared receiving
code, ordered Events queue and the supported event families. It remains a
library implementation, not a completed iOS host/APNs integration.

Current decisions and remaining work are maintained in:

- [NSE integration and verification](../../nse/README.md)
- [Account ownership and replay decisions](../adr/0011-nse-account-runtime-ownership.md)
- [Current coverage and exact dependencies](../NSE_IMPLEMENTATION_STATUS.md)

The chronological milestones in earlier versions of this file are historical.
They include removed receipt/checkpoint and inline-proposal designs and should
not guide new code. Git retains that history. Resolve an evidenced dependency
from the current coverage table, or extend a concrete caller; do not add generic
runtime layers, extra queues or journals for possible future work.
