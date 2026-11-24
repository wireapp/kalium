HTTP request and response logs now record only the URL path and query. The scheme, host, port, user info and fragment are dropped. Each path segment that is not a known static API segment, such as an ID, a domain or an asset key, is logged as `{}`. Each query value keeps only its first 3 characters as a hint, such as `client=5f2***`, and values of 3 characters or fewer are logged as `***`. `cursor` and `client` values are no longer logged in clear.

  - ABI: no change
  - Source: no change
  - Behavior: log lines change from `nginz-https.example.com/v5/users/example.com/<id>?client=<id>` to `/v5/users/{}/{}?client=5f2***`. Update log parsers or dashboards that read the `endpoint` field.
