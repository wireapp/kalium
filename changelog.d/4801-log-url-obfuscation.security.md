HTTP request and response logs now record only the URL path and query. The scheme, host, port, user info and fragment are dropped. Path segments made of lowercase letters and hyphens, such as `conversations`, and version segments such as `v9` stay readable. Every other segment is obfuscated with the logger's existing rules: a domain keeps 3 characters (`ant***`) and an ID or asset key keeps 7 (`9a1d6c0***`). A segment no longer than its hint is logged as `{}`. Each query value keeps only its first 3 characters as a hint, such as `client=5f2***`, and values of 3 characters or fewer are logged as `***`. `cursor` and `client` values are no longer logged in clear.

  - ABI: no change
  - Source: no change
  - Behavior: log lines change from `nginz-https.example.com/v5/users/example.com/<id>?client=<id>` to `/v5/users/exa***/9a1d6c0***?client=5f2***`. Update log parsers or dashboards that read the `endpoint` field.
