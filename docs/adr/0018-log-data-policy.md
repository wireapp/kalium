# 18. Log data policy

Date: 2026-11-23

## Status

Proposed

The data table is proposed for acceptance. The treatment of identifiers (see "Open decision") is a recommendation that awaits team acceptance.

## Context

No document says what may appear in a Kalium or app log. `ObfuscateUtil.kt` says identifiers may be logged in clear "according to the agreed policy", but that policy is not recorded anywhere. The Kalium README says unique identifiers are obfuscated. `Extensions.kt` keeps a prefix of each ID and leaves short values unchanged. These three statements disagree.

The code paths that lead to logs are broad. `KaliumLogger` takes a raw `String`, formats user and client IDs into tags, and forwards throwables. `KaliumHttpLogger` keeps any header that is absent from a deny list. `ObfuscateUtil` keeps URL hosts, paths and some query values. Apps add their own sinks, and some code bypasses `KaliumLogger`.

The planned sanitizing writer, canary tests and typed log API need one table to test against. Kalium owns the shared logger, so Kalium owns that table. Apps own their sinks and link to this ADR.

## Decision

Apply this policy at every log level, including debug builds. It covers messages, tags, structured attributes, HTTP metadata, exception messages, cause chains and third-party logger integrations.

| Data | Treatment |
| --- | --- |
| Passwords, cookies, tokens, keys, authorization headers, signing credentials | Omit the value. A fixed marker may record that redaction occurred. Never keep a prefix. |
| Message content, attachment names, user-entered text, emails, phone numbers, local file paths | Omit. Log an event name, a content category or an error code instead. |
| Customer domains, backend hostnames, tenant names, federation domains | Omit. Correlation follows the identifier decision below. |
| User, client, team, conversation, message and asset IDs, including qualified IDs | Decided in "Open decision". A qualified ID is sensitive as a whole. |
| URLs | Log a known route template, method and status code. Drop the hostname, dynamic path segments, user information, query values and fragment. |
| Header and JSON fields | Allow only approved fields and value types. Unknown fields are omitted. A safe field name does not make an arbitrary string safe. |
| Durations, counts, protocol versions, fixed event and error names | Allow through a reviewed field allowlist. Review combinations that could identify a customer or user. |

Kalium owns the shared policy and the code that enforces it. Each app owns its sinks and must satisfy this table for each of them. The enforcement mechanism (sanitizing writer, typed API, canary tests, static rules) is outside this ADR.

### Open decision: how IDs are logged

Three options. The team has not chosen.

| Option | Benefits | Costs |
| --- | --- | --- |
| Clear | Matches today's code (`ObfuscateUtil`). Support can search logs by ID. No key to manage. | Logs reveal who talked to whom and when. Exported bundles and DataDog hold direct identifiers. Conflicts with the README. |
| Omit | Nothing to leak. Simplest to test: no ID shape may appear. | Cannot correlate events across entries for one user or conversation. Some support cases lose their only handle. |
| Per-install pseudonym | Correlation without the real value. A pseudonym cannot be reversed without the key. | Needs a key, its storage and rotation (LOG-1.2). A pseudonym stable per install links entries across restarts and across bundles from one device, so it is linkable data, not anonymous. Support cannot map a pseudonym back to a user without help. |

Pseudonyms are stable per install, not per session (decision of 2026-11-17). The earlier proposal scoped them to one session. A per-install alias persists across restarts. Rotation, for example on logout or account change, belongs to LOG-1.2.

Recommendation, awaiting team acceptance: omit IDs by default. Use per-install pseudonyms only where a documented diagnostic need exists, with a separate namespace per identifier type. Plain hashes and stable plaintext prefixes are not acceptable defaults.

## Consequences

- One table replaces three conflicting statements. Later work (sanitizing writer, canary tests, typed API) tests against it.
- If accepted as recommended, the comment and behavior in `ObfuscateUtil` that keep IDs in clear must change. A later ticket changes the behavior. This ADR changes only the comment, which now says the policy is under review.
- Omitting IDs removes a debugging handle. Each documented diagnostic need must name the event and the identifier type it needs.
- Pseudonyms add key management and a retention question for old bundles. LOG-1.2 covers them.
- Retained and exported logs are not covered here. Old rolling files, uploaded telemetry and exported bundles stay unsanitized until LOG-1.3 decides. Do not claim every export is sanitized.
