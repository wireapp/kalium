Added optional `KaliumConfigs.httpTrafficObserver` for transport-level debugging on JVM/Android. It exposes unredacted URLs, headers and bodies. Apple and JS accept the option but do not observe traffic. The default remains disabled and production HTTP logging keeps its redaction behavior.

Non-duplex uploads are captured during the actual send, including one-shot streams. Duplex, unsent and failed uploads report a null body. Gzip responses are decoded for observation and remain readable by the caller. This debugging hook buffers bodies in memory; callback implementations must not throw.

  - ABI: `KaliumConfigs` constructor and copy signatures change. Recompile Kotlin/JVM consumers.
  - Source: the optional parameter is appended, preserving existing positional arguments and component positions.
  - Migration: pass an observer only when full traffic inspection is intended. Implementing observer callbacks requires `InternalKaliumApi` opt-in.
