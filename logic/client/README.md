# Kalium client composition

`logic:client` is the explicit full-client entry point. It constructs the existing `CoreLogic`
composition on JVM, Android, and Apple and preserves every platform constructor input, including a
custom `UserAuthenticatedNetworkProvider`.

For the pre-calling-team-confirmation pass, dependency ownership remains transitional:

```text
logic:client -> logic
```

This keeps the existing `logic` artifact, class locations, constructors, and ABI intact while new
consumers can adopt the named client composition. Reversing ownership would move the complete
multiplatform client implementation between published artifacts. That change is deferred together
with the client regression/performance comparison because this pass explicitly does not add,
change, or run tests.

After calling-team confirmation, move the existing wiring into `logic:client`, change `logic` into
a delegating compatibility artifact, run the full client regression and performance suite, and
verify migration for direct Swift/XCFramework consumers before publishing that reversal.
