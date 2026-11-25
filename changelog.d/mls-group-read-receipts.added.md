Read receipts are sent in MLS group conversations with read receipts enabled, when `KaliumConfigs.targetedAndTransientMLSMessagesEnabled` is `true` and API version is v19 or above. Each receipt is a targeted MLS message sent only to the sender of the read messages. When the flag is `false`, MLS group conversations still don't send read receipts.

  - Migration: no action required.
