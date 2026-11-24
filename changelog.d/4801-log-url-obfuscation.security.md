HTTP request and response logs now record only the URL path and query keys. The scheme, host, port, user info and fragment are dropped. Each path segment that is not a known static API segment, such as an ID, a domain or an asset key, is logged as `{}`. Each query value is logged as `***`, with no prefix.

  - ABI: no change
  - Source: no change
  - Behavior: log lines change from `nginz-https.example.com/v5/users/example.com/<id>?client=<id>` to `/v5/users/{}/{}?client=***`. Update log parsers or dashboards that read the `endpoint` field.
