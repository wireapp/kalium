# 18. Log data policy

Date: 2026-11-25

## Status

Proposed

## Context

No document says what a log may contain, so each case is decided in review. Logs reach logcat, log files that users send to support, and DataDog. Android sends logs to DataDog from internal builds only. prism's iOS app also sends them from production builds. Today they can hold credentials, customer domains and IDs. HTTP failure logs print the exception message, and Ktor messages carry the full URL, query credentials included (kalium#4786).

`ObfuscateUtil` said IDs may be logged in clear "according to the agreed policy", but that policy was never written down. Lint rules, canary tests and reviews need one table to check against.

Kalium writes most logs and owns the shared logger, so the policy lives here. Applications link it and do not copy it.

## Decision

A log line follows the rule for each kind of data it carries.

| Data | Prod logs |
| --- | --- |
| Credentials: tokens, push tokens, guest link `code` and `key`, signatures, passwords, `access_token` | Never. Fully masked (`***` or `{}`), no hint. |
| IDs: user, conversation, client, team | The first 7 characters, then `***` (`9a1d6c0***`). |
| Domains inside paths and IDs | The first 3 characters, then `***` (`ant***`). |
| Backend host | Wire-owned hosts (`*.wire.com`, `*.wire.link`, `*.zinfra.io`) in clear. Customer hosts get the domain hint. |
| Query values | Paging numbers (`size`, `page`, `limit`, `offset`) in clear, ASCII digits only. `client`, `cursor` and `since` keep a 3-character hint. Every other value, credentials included, is fully masked. |
| Exception messages | HTTP failure logs show the exception class only. Other exception logs keep the full message for now. |
| Content: message text, file names, file paths, emails, phone numbers, handles | Never, not even a hint. Log a type, size or count instead. |
| Event names, error codes, counts, durations | Allowed. |

```text
before: nginz-https.acme-corp.com/v9/users/acme-corp.com/9a1d6c0e-…?client=5f2b9e7c4d1a
after:  ngi***/v9/users/acm***/9a1d6c0***?client=5f2***
```

Rules that apply to every row:

- The table applies to every level, sink, tag, structured attribute and exception. One exception for now: exception messages outside HTTP failure logs are logged in full, and they can hold file paths or other content. A later change brings them under the content row and revises this ADR.
- Non-prod builds follow the prod rules. Kalium does not know the build type yet. When it does, a non-prod build may log more, never credentials. That change needs a revision of this ADR.
- Hints are prefixes of the real value, so they are stable across installs and log lines.
- A value no longer than its hint is fully masked, because the hint would show all of it.
- Changes to the policy go through this ADR. Code does not restate the table. It links here.

## Consequences

Reviews, lint rules and canary tests check against one table. Support can still match log lines that belong to the same conversation or device, because IDs and domains keep a short prefix.

A short prefix is still real data, and 7 characters narrow an ID a lot. We accept that for support. A keyed hint per install, replacing the prefix, is possible later work. It is not decided here and would revise this ADR.

Retained logs, meaning files already on devices and in support bundles, are not covered. Each application decides how to treat them.

Existing code that logs more than the table allows is a bug against this ADR. The sanitizing code and the tests that enforce the table follow in separate changes.
