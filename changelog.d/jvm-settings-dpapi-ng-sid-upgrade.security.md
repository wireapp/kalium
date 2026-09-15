JVM on Windows: a settings master key that could only be protected to the local user, because the SID wasn't available then, is now protected to the user's SID as well once it is.

  - ABI: no change
  - Source: no change
  - Behavior: at startup, when the protected key names no SID and protecting to `SID=<user> OR LOCAL=user` works now, for example with the domain controller in reach or a KDS root key added since, the `settings-master-key` file is rewritten with the newly protected key. The key itself stays the same.
  - Migration: none needed.
