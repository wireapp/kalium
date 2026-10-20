Added per-device raised-hand state for active group calls and `CallsScope.setHandRaised` for sending hand updates.

- ABI: changes public call models and adds hand-state and signaling types; consumers must rebuild.
- Source: consumers exhaustively matching message content or signaling types must handle `InCallHandRaise`.
- Behavior: raised hands follow locally observed order and clear when participants leave or the call ends. Late joiners start with empty hand state. Signals are not persisted as chat messages; the NSE ignores them.
- Migration: rebuild consumers and use the active call generation when sending or explicitly retrying a hand update. UI integration remains the consumer's responsibility.
