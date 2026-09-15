JVM: fixed that apps couldn't adjust the OkHttp clients Kalium builds, for example to authenticate at a proxy with the Windows logon or to trust the system's certificate store. `OkHttpClientCustomization.customizer` now adjusts every one of them, the WebSocket's included.

  - ABI: additive
  - Source: additive
  - Behavior: no change unless a customizer is set. It runs after Kalium's own settings, so it can override them.
  - Migration: none needed.
