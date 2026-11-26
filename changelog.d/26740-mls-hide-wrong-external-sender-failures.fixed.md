When an MLS message fails its signature check in a group hosted on the self backend whose external sender is not the backend's removal key, the group is reset so it uses the right key, and the failure no longer shows as "message could not be decrypted". If the group can't be reset (another backend hosts it, the reset is disabled or it fails), the failure is still shown.

  - ABI: unchanged
  - Source: unchanged
  - Behavior: invalid signature failures caused by a wrong external sender reset the group instead of being shown to the user
