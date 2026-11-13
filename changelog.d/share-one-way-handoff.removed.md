Removed the result half of the Apple share outbox: `KaliumShareOutbox.handOffStatus` and `stopWaiting`, `KaliumShareHandOffStatus` and `KaliumShareHandOff`. A share handed to the app is the app's from then on.

  - ABI: breaking for the KaliumShare Apple framework; the removed types and methods are gone. `KaliumShareOutbox.appTookHandOff` is added.
  - Source: breaking for hosts that follow a handoff.
  - Behavior: the app reports every handed-off share that ends without a stored message through `refused`, not only those the host stopped following. Serving the outbox deletes a request for conversations, or its answer, that is more than a minute old, and the result files earlier builds left.
  - Migration: after `handOff`, ring the app and finish; `appTookHandOff` tells whether a running app claimed the share.
