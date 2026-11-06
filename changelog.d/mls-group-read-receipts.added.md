Read receipts are sent in MLS group conversations with read receipts enabled, when `KaliumConfigs.targetedAndTransientMLSMessagesEnabled` is `true`. Each receipt is a targeted MLS message sent only to the sender of the read messages. When the flag is `false`, MLS group conversations still don't send read receipts.

  - Behavior: read receipts received as persistent targeted MLS messages are now processed, whatever the flag is set to. Transient read receipts and targeted delivery receipts are still rejected. Sending is unchanged unless `targetedAndTransientMLSMessagesEnabled` is `true`. Proteus, mixed and 1:1 conversations are unchanged.
  - Migration: no action required.
