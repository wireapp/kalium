Cells uploads and downloads reuse the current access token. A rejected token triggers one refresh and retry, and concurrent rejections share the refreshed token. Multipart uploads no longer refresh the token for every part.

  - ABI and source: unchanged.
  - Migration: no action required.
