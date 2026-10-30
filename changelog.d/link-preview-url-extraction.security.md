Link previews now extract complete HTTP(S) and `www.` URL candidates without a recursive URL regex. Ambiguous URL authorities are rejected instead of previewing a shorter, different hostname.

  - ABI/Source: unchanged.
  - Behavior: detection scans at most the first 8,000 UTF-16 characters and drops candidates cut by that limit. FTP and unprefixed address literals are no longer preview targets. Unicode hostnames must use their ASCII/punycode form; Unicode paths remain supported.
  - Migration: no action required.
