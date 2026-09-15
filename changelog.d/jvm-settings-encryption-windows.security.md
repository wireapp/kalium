JVM on Windows: the master key of the encrypted settings is now protected with DPAPI-NG for the current user and kept in the `settings-master-key` file.

  - ABI: no change
  - Source: no change
  - Behavior: in an Active Directory domain with a KDS root key, the key is also protected to the user's SID, so the user can unprotect it on every computer of the domain, with a new or non-persistent profile too. Otherwise it is protected to the local user and follows the user profile (roaming profiles, Citrix profile management). When the key can't be unprotected, Kalium throws `SettingsEncryptionException`.
  - Migration: none needed.
