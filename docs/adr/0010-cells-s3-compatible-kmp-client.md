# 10. Cells S3-Compatible KMP Client

Date: 2026-07-01

## Status

Implemented

## Context

Kalium's Cells object transfer code used the AWS SDK Kotlin S3 client on JVM and Android, while
Apple targets had no-op implementations for Cells uploads, downloads, and pre-signed object URLs.
Using the AWS Swift SDK on iOS was technically possible, but it introduced a large dependency graph
for a narrow S3-compatible surface.

The Cells integration only needs a small subset of S3 operations: get object, put object, create
multipart upload, upload part, complete multipart upload, and pre-sign get object. The existing
module already depends on Ktor and Okio, which are sufficient for HTTP streaming and SigV4 hashing.

## Decision

Implement a small internal S3-compatible client in `:domain:cells` for Apple/KMP object transfers
instead of adding the AWS Swift SDK. Keep S3 details behind `CellsAwsClient` and scope the client to
the exact operations used by Cells.

The client uses path-style S3 requests against the configured Cells backend, AWS Signature Version 4
with `UNSIGNED-PAYLOAD`, Ktor for HTTP requests, and Okio for streaming and SHA-256/HMAC-SHA256.
JVM and Android keep the existing AWS SDK Kotlin implementation.

## Consequences

Cells uploads, downloads, multipart uploads, and pre-signed download URLs can work on Apple targets
without pulling in the AWS Swift SDK dependency graph.

The tradeoff is that Kalium now owns a small SigV4/S3 implementation for this limited use case.
Focused tests cover canonical signing behavior and request construction, and future S3 features
should stay inside `:domain:cells` unless a broader SDK dependency is explicitly accepted.
