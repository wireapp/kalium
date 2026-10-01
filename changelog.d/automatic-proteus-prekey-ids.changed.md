Proteus prekey refill now lets CoreCrypto select available IDs, avoiding duplicate-ID failures with CoreCrypto 10.5.1.

  - ABI and source: removed `ProteusClient.newPreKeys(from, count)` from the cryptography module.
  - Migration: use `ProteusClient.newPreKeysAuto(count)` for rolling prekeys. Client registration already uses automatic IDs.
