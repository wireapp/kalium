A failed sync now retries right away when the device moves to another validated network, for example from Wi-Fi to mobile data, instead of waiting out its backoff of up to 10 minutes. `NetworkStateObserver.delayUntilConnectedWithInternetAgain` also completes when a validated network other than `previousNetworkId` becomes the current one.

  - ABI: `delayUntilConnectedWithInternetAgain` gains a `previousNetworkId` parameter that defaults to the current network.
  - Source: callers can omit it. Implementations that override the method must add the parameter.
  - Migration: none for callers.
