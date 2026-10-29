Accepted connection requests no longer stay pending. Accepting a request, or the other user accepting ours, updated the user's connection status but left the stored request in its earlier state, so `ObservePendingConnectionRequestsUseCase` kept returning it and removing a federated backend treated it as a pending request. The stored request now becomes `ACCEPTED` together with the user, in one transaction, in the app and in the notification processor. A user database migration fixes requests that were already accepted: requests of accepted users whose conversation is no longer a pending connection. Users marked accepted only by team or collaborator sync keep their pending requests.

  - ABI: unchanged.
  - Source: `ConnectionDAO.updateConnectionStatus` added.
  - Migration: user database migration 165; no host change needed.
