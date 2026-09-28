# Kalium network performance and protocol quality review

Date: 2026-09-28
Base commit: `7026a46`
Scope: `data/network`, `data/network-util`, `data/network-model`, the sync, event, message, asset and connection code under `logic/`, and `domain/cells`.
Method: source read of the full request path per area, with every finding checked against the code at the quoted line. Ktor internals were checked against the Ktor 3.5.0 sources rather than from memory. Nothing was benchmarked; the numbers in this document are request counts and byte counts derived from the code, not measurements.

## 1. Verdict

The transport layer is sound at the wire level: HTTP/2 via OkHttp with `RESTRICTED_TLS`, certificate pinning on OkHttp and Darwin, gzip on by default, protobuf envelopes for Proteus, `message/mls` for MLS, streamed asset bodies, batched prekey and client lookups, and a single-flight token refresh shared by REST and websocket clients. The happy path for sending a message is one HTTP request with zero metadata lookups.

The weaknesses are almost all one level up, in how the library behaves when things go wrong or get big:

- **Resilience is coarse.** There is no per-request timeout, retry, jitter or `Retry-After` handling anywhere in the Wire clients. Failure handling lives in the sync managers, which retry whole sync units after a lock-step exponential backoff.
- **Slow sync does not checkpoint, and incremental sync cannot skip a bad event.** A transient error in slow sync step 10 repeats steps 1 to 9, including a full team member walk at 200 per page. One event whose handler fails deterministically stalls the incremental pipeline indefinitely.
- **Network I/O happens inside the exclusive CoreCrypto transaction.** A send can hold the crypto lock for up to six round trips, and in the MLS stale-epoch path it waits for sync to become live while holding the lock the sync worker needs.
- **Several correctness bugs on the edges.** Token refresh is cancellable and can drop a rotated refresh cookie, an asset upload that hits a 401 replays an exhausted stream, the encrypt helper reports success on failure, and API v17 quietly builds a second OkHttp client per session.

None of these are exotic. Most are fixable in isolation with small, testable changes. Section 3 ranks them.

## 2. How the stack fits together

Per user session `AuthenticatedNetworkContainer.create` builds one engine (`OkHttp` on JVM and Android, `Darwin` on Apple, `Js` on web) and one `BearerAuthProvider`, shared by three Ktor `HttpClient`s: the normal client, a no-compression client for assets, and a disposable client per websocket connection. All responses pass through `wrapRequest` in `HttpResponseHandler.kt`. Retries and backoff exist only in `SlowSyncManager` and `IncrementalSyncManager` via `ExponentialDurationHelper`.

Key constants as found:

| Setting | Value | Where |
|---|---|---|
| OkHttp connect / read / write timeout | 30 s (websocket constant reused for all REST) | `data/network/src/*/OkhttpClientFactory.kt` |
| Ktor `HttpTimeout` on Wire clients | not installed | `NetworkClient.kt` |
| OkHttp connection pool | 15 idle, 1 min keep-alive, per engine | `commonJvmAndroid/HttpEngine.kt:53` |
| OkHttp `maxRequestsPerHost` | 15 | `commonJvmAndroid/HttpEngine.kt:54` |
| Websocket ping | 20 s | `NetworkClient.kt:195` |
| Sync backoff | 1 s to 10 min, factor 2, no jitter | `ExponentialDurationHelper.kt` |
| Conversation page | 1000 ids | `ConversationApiV0.kt` |
| User batch | 500 ids | `UserRepository.kt:718` |
| Team members page | 200 | `TeamRepository.kt:77` |
| External message threshold | 200 KiB estimated total | `MessageEnvelopeCreator.kt` |
| Key package claim concurrency | 8 | `KeyPackageRepository.kt:212` |

## 3. Priority list

Ranked by expected impact on users and backend load, weighted by how cheap the fix is.

| # | Finding | Severity | Effort | Section |
|---|---|---|---|---|
| 1 | Network I/O and a sync wait run inside the exclusive CoreCrypto transaction | High | Medium | 6.1 |
| 2 | One poison event stalls incremental sync forever; whole 500-event batch is one non-cancellable transaction | High | Medium | 5.2 |
| 3 | Token refresh is cancellable; a rotated refresh cookie can be lost, forcing logout | High | Small | 4.1 |
| 4 | Slow sync restarts from step one on any failure, with no checkpoint | High | Medium | 8.1 |
| 5 | Asset upload replays an exhausted stream after a 401 refresh | High | Small | 7.1 |
| 6 | Read timeouts after the server accepted a send are classified as "no network" and resent | High | Small | 6.2 |
| 7 | Response headers, including `Set-Cookie`, are logged unredacted when request logging is on | High | Small | 9.1 |
| 8 | Per-event acks with no failure path; `multiple` never used during catch-up | High | Small | 5.3 |
| 9 | One `GET /conversations/{id}` per pending outgoing connection during slow sync | High | Small | 8.2 |
| 10 | No `HttpTimeout`, no transport retry, no jitter, `Retry-After` ignored | Medium | Small | 4.2 |
| 11 | Every websocket frame is JSON-parsed twice, once for a discarded verbose log | Medium | Trivial | 5.1 |
| 12 | Every websocket reconnect costs an extra HTTP call and leaks an `HttpClient` | Medium | Small | 5.4 |
| 13 | Legacy catch-up pages at 100 and restarts from zero on an empty page | Medium | Small | 5.6, 5.7 |
| 14 | API v17 branch discards the shared engine and traffic observer | Medium | Trivial | 4.4 |
| 15 | Enabling the Meetings flag triggers a full slow sync on every client in the team | Medium | Small | 8.4 |
| 16 | Three extra full-file passes on asset upload for hashing; two plaintext copies | Medium | Small | 7.2 |
| 17 | Error responses are deserialised up to five times using exceptions as control flow | Medium | Small | 4.5 |
| 18 | Link preview client bypasses the configured SOCKS proxy | Medium | Small | 9.3 |

## 4. Transport, auth and response handling

### 4.1 Token refresh is cancellable and can lose a rotated refresh token (High)

`AuthenticatedNetworkContainer.kt:432` constructs `BearerAuthProvider(refreshToken, loadToken, { true }, null)`. In Ktor 3.5.0 the remaining constructor defaults are `cacheTokens = true, nonCancellableRefresh = false`, so the refresh runs in the cancellable context of whichever request tripped the 401. `SessionManagerImpl.updateToken` (`logic/.../network/SessionManagerImpl.kt:63,94-118`) runs under `limitedParallelism(1)` but adds no `NonCancellable`. The new refresh token arrives as a `Set-Cookie` on `POST /access` (`AccessTokenApiV0.kt:37-40`).

If the owning coroutine is cancelled (screen closed, collector cancelled, sync restarted) after the backend has rotated the cookie but before `persistTokens` runs, the new token is dropped and the in-memory holder keeps the old one. The next refresh with the stale cookie returns 403, which `SessionManagerImpl.kt:136` maps to `onSessionExpired()` and a forced logout.

Fix: pass `nonCancellableRefresh = true` to the provider, and wrap `refreshTokenAndPersistSession` in `withContext(NonCancellable)`. Add a test that cancels the caller mid-refresh and asserts the persisted token is the rotated one.

### 4.2 No per-request timeouts, no transport retry, no jitter, `Retry-After` ignored (Medium)

- `provideBaseHttpClient` (`NetworkClient.kt:165-190`) installs no `HttpTimeout` or `HttpRequestRetry`. The only `HttpTimeout` in the repo is the link preview client.
- OkHttp gets `WEBSOCKET_TIMEOUT` (30 s) as connect, read and write timeout for every REST call (`OkhttpClientFactory.kt`). There is no `callTimeout`, so a slow-drip response holds a dispatcher slot indefinitely.
- Darwin and JS engines have no timeout configuration at all, so iOS falls back to NSURLSession's 60 s idle timeout and never times out on connect.
- `ExponentialDurationHelperImpl.next()` returns exact `1, 2, 4 ... 600` seconds. `SyncRetryDelay.kt:46-48` resets to 1 s on reconnect for every client, so after an outage the whole fleet retries in lock-step.
- `Retry-After` is not read anywhere. `JoinExistingMLSConversationsUseCase.kt:149-156` retries 429 after a fixed 250 ms.

Fix: install `HttpTimeout` in `provideBaseHttpClient` (connect 10 to 15 s, socket 30 s, request 60 s) with per-request overrides for asset transfers; install `HttpRequestRetry` for idempotent methods on `IOException`, 502, 503, 504 and 429 with exponential backoff, full jitter and `respectRetryAfterHeader`; multiply the sync backoff by a random factor in `[0.5, 1.5]` (the Cells S3 client already does this at `CellsS3Client.kt:535-538`).

### 4.3 Refresh failure has no cool-down; out-of-band refreshes race the Ktor one (Medium)

When `/access` fails with a 5xx or `IOException`, `updateToken` throws, the old token stays cached, and the next 401 refreshes again. With the backend unhealthy, every authenticated call costs two round trips. Separately, `SessionRefreshSuggestedEventHandler.kt:37-42` and `UpgradeCurrentSessionUseCase.kt:44-49` refresh directly and then call `clearCachedToken()`, whose Ktor implementation clears asynchronously on `GlobalScope` if the mutex is held. Between the out-of-band persist and the clear, a 401-triggered refresh sends the stale cookie.

Fix: memoise refresh failures for a short growing window and fail fast, or return `null` from `refreshTokens` so Ktor surfaces the 401 without replay. Route every refresh through one single-flight entry point in `SessionManagerImpl` that reads the refresh token from storage.

### 4.4 API v17 branch drops the shared engine (Medium, trivial fix)

`AuthenticatedNetworkContainer.kt:359-367` is the only branch that does not pass `engine = engine`, so on a v17 backend the precomputed engine (with its pool, dispatcher, pinning and proxy) is thrown away and `AuthenticatedNetworkContainerV17` builds a second one without the `HttpTrafficObserver`. One extra OkHttp client, pool and thread pool per session. Add the missing argument and a test that iterates every supported version.

### 4.5 Error responses are parsed up to five times (Medium)

`HttpResponseHandler.kt:79-88` reads the error body once as text, then chains `UnauthorizedResponseInterceptor`, `federationErrorResponseInterceptor`, `MLSErrorResponseHandler`, the custom interceptor and `BaseErrorResponseInterceptor`. Each one runs its own `json.decodeFromString<T>(body)` and treats `SerializationException` as "not mine". The lazily parsed `jsonBody` in `HttpResponseData.kt:46` is unused. This sits on the message send path: every 412 client-mismatch reply pays several parses and thrown exceptions.

Fix: parse once into `JsonElement`, dispatch on `label` or shape, then `decodeFromJsonElement`.

### 4.6 Smaller transport items (Low)

- Android's `OkHttpSingleton` (`androidMain/OkhttpClientFactory.kt:29-44`) intends to share one client, but `defaultHttpEngine` then overrides both `connectionPool` and `dispatcher`, so nothing is shared across the Authenticated, Unauthenticated, Unbound and Transient containers or across accounts. Keep-alive is 1 min against OkHttp's 5 min default, so any minute of quiet costs a new TLS handshake. Share one pool and dispatcher, and raise keep-alive.
- `Authenticator.setDefault` (`commonJvmAndroid/HttpEngine.kt:88`) is process-global; the last account to initialise wins. Use OkHttp's per-client `proxyAuthenticator`.
- `KaliumException.NoNetwork` is never produced; offline failures arrive as `GenericError(IOException)` after a full pipeline walk. Either delete it or add a cheap send-pipeline check against `NetworkStateObserver`.
- `Url(links.api)` is re-parsed inside the `defaultRequest` lambda on every request (`WireDefaultRequest.kt:33-36`). Hoist it.
- `cellsHttpClient` defaults to `get() = HttpClient()` on the interface (`AuthenticatedNetworkContainer.kt`), so containers V0 to V11 would allocate a fresh client on each access. Only V12 and later override it, and Cells is not offered on older backends, so this is latent rather than live.
- `KtxSerializer` uses `encodeDefaults = true`. Defensible for a backend that expects the fields, but worth a payload survey on `POST /proteus/messages` before deciding.

## 5. Event pipeline and websocket

Two transports are selected at runtime. Backends below API v9 use `GET /notifications/last`, a websocket on `/await`, then paged `GET /notifications?size=100&since=...`. API v9 and later use the consumable stream: `GET /cookies` as a token-refresh nudge, then `wss .../v{N}/events?client=...&sync_marker=<uuid>`, with `event`, `synchronization` and `notifications_missed` frames and acks as text frames on the same socket. In both cases every event is written to the `Events` table first with `INSERT OR IGNORE` on a unique `event_id`, then processed from a DB-backed flow in batches of up to 500 inside one Proteus plus MLS transaction, and marked processed only after the whole batch succeeds.

The design is right: persist-before-process gives at-least-once delivery with idempotent replay, the `sync_marker` handshake rejects stale markers, missed-notification and 404 recovery both funnel into a persisted slow sync restart, and the ping interval is set consistently in Ktor and OkHttp. The problems are in batch granularity, ack granularity, and reconnect cost.

### 5.1 Every websocket frame is JSON-parsed twice (Medium, trivial fix)

Covered in 9.2. `deleteSensitiveItemsFromJson` runs eagerly on every frame to build a verbose log string that is discarded at any normal log level, doubling the parse cost of every incoming event.

### 5.2 A whole batch of up to 500 events is one non-cancellable transaction, and one poison event stalls sync (High)

`IncrementalSyncWorker.kt:77-107` wraps the batch in `withContext(NonCancellable)`, opens one crypto transaction, mutes DB invalidation, folds through `processEvent` with `foldToEitherWhileRight`, and throws `KaliumSyncException` on any `Left`. Nothing is marked processed on failure.

Three consequences. Cancellation on app background or slow sync restart waits for up to 500 decrypts. The UI sees no invalidation until the batch completes. And a handler that fails deterministically for one event, for example a `MemberJoin` whose `fetchConversation` returns a permanent error, aborts the batch, sync backs off up to 10 min, re-reads the same batch and fails again, forever. Events that succeeded earlier in the batch are re-run on every retry, because only the crypto transaction is rolled back, not their DB side effects.

Fix: process in sub-batches of around 50 and mark processed per sub-batch; keep `NonCancellable` only around the commit; on a per-event failure decide explicitly between skip-and-mark and abort, with a failure counter so a single bad event cannot wedge sync.

### 5.3 Acks are one per event, fire-and-forget, with no failure path (High)

`EventRepository.kt:283-309` inserts each event, then on success sends an ack and writes `last_processed_event_id`, one frame and one metadata write per event. `EventRepository.kt:353` hard-codes `multiple = false` with a TODO. `NotificationApiV9.kt:83` uses `session.outgoing.trySend` and only logs on failure. Ktor's outgoing channel is unlimited, so `trySend` fails only on a closed socket, where the server will redeliver and `INSERT OR IGNORE` absorbs the duplicate. The gap is the other direction: `insertEvents` failure has no `onFailure` branch, so a storage error leaves the event unacked and unprocessed until the next reconnect, silently.

During catch-up of thousands of events this is also two frames on the socket per event, plus a mutex, a JSON encode and a `currentClientId()` lookup per ack.

Fix: during `CatchingUp` ack the last `delivery_tag` with `multiple = true` per DB batch or per time window; use the suspending `send` so a closed channel surfaces; on storage failure close the socket to force redelivery.

### 5.4 Every reconnect costs an extra HTTP round trip and leaks an `HttpClient` (Medium)

`NotificationApiV9.kt:96-108` issues `GET /cookies` before every socket purely to trigger token refresh, because websocket upgrades cannot be intercepted by the Auth plugin. The legacy path does the same with `GET /notifications/last`. `NetworkClient.kt:138-158` then creates a disposable `HttpClient` per connection, and `NotificationApiV9.kt:124-125` only cancels the session on completion, never `close()`s the client. On a flaky network with a 1 s minimum backoff, clients accumulate.

Fix: refresh through `BearerAuthProvider` directly before connecting, and close the disposable client in `onCompletion`.

### 5.5 Reconnect backoff has no jitter and resets on reconnect (Medium)

`IncrementalSyncManager.kt:97-98` uses the same jitterless helper as slow sync, and `SyncRetryDelay.kt:46-49` resets it the moment `ConnectedWithInternet` is observed. After a gateway restart every client retries at exactly 1, 2, 4 seconds; after a network handover every client on that network reconnects at t=0 against `/cookies` and the websocket upgrade. The early wake-up is good, but it sharpens the herd. Add jitter and a small random delay after the reconnect early exit.

### 5.6 Legacy catch-up pages at the server minimum of 100 (Medium)

`EventRepository.kt:527` sets `NOTIFICATIONS_QUERY_SIZE = 100`, which `NotificationApiV0.kt:200-204` describes as the backend minimum. The server default is 1000. A 5,000 event backlog is 50 sequential round trips and 50 transactions, and `ReadyToProcess` is only emitted after the whole loop. Raise to 500 to 1000 and consider emitting `ReadyToProcess` after the first page, since autoincrement ordering already preserves order.

### 5.7 Legacy pagination restarts from the beginning on an empty page (Medium)

`EventRepository.kt:434-435`:

```kotlin
hasMore = notificationsPageResult.value.hasMore
lastFetchedNotificationId = notificationsPageResult.value.notifications.lastOrNull()?.id
```

An empty page with `has_more = true` sets the cursor to `null`, and `getNextPendingEventsPage` (`:506-507`) then calls `getAllNotifications` from the start of the stream. Keep the previous cursor on an empty page and break or fail if `has_more` is true with no notifications.

### 5.8 Payload is parsed and serialised three times per event, and re-queried on every insert (Medium)

`EventContentDTO.kt:95-101` decodes the payload to a `JsonElement` and re-encodes it to a string for storage; `EventRepository.kt:191-195` parses it again on read; the frame handler parses it a third time for the log (5.1). Separately, `observeUnprocessedEvents` (`Events.sq:45-49`) is a SQLDelight `asFlow` over `SELECT ... WHERE is_processed = 0 ORDER BY id LIMIT 500` with no index on `is_processed`, re-run on every insert during a burst and mostly discarded by the `lastEmittedEventId` filter. Processed rows are only pruned on the next socket open. Store the raw payload substring, add an index on `(is_processed, id)`, prune after each batch, and re-query only after `markEventsAsProcessed`.

### 5.9 Smaller event items (Low)

- `acknowledgeEvents` (`NotificationApiV9.kt:79-80`) can open a brand new socket as a side effect if it races socket closure, and `session = null` in `onCompletion` is outside the mutex. Fail fast when there is no session.
- `notifications_missed` is acked with `ack_full_sync` before the recovery flag is persisted (`EventRepository.kt:313-317`); the write result is unchecked. Persist first, ack second.
- Retry in both sync managers is recursion inside the exception handler (`IncrementalSyncManager.kt:151-163`, `SlowSyncManager.kt:146-158`), so days offline grow the continuation chain. Use a loop.
- Both transports buffer the live stream with `Channel.UNLIMITED` (`EventRepository.kt:400-402`, `EventGatherer.kt:130`). Acceptable in practice, unbounded by design.
- `MockWebSocketSession` ships in `commonMain`, and `EventProcessingHistory` is dead code.

## 6. Message sending

Happy path is good: recipients and clients come from the local DB (`ConversationRepository.kt:753-756`), session existence is checked against an in-memory cache, encryption is one batched CoreCrypto call, the envelope is protobuf sent as `application/x-protobuf` through a pass-through converter, and external messages kick in above an estimated 200 KiB total. One request, no metadata lookups. The mismatch path is up to six round trips with a single retry.

### 6.1 Network I/O and a sync wait inside the exclusive CoreCrypto transaction (High)

`MessageSenderImpl.kt:128` opens `transactionProvider.transaction("sendMessage")` and everything happens inside it: `sendEnvelope` (`:376`), the 412 handler with its three metadata calls plus `fetchConversation` (`:468`), prekey fetching (`:231`), and for MLS the stale-epoch path at `:330`:

```kotlin
staleEpochVerifier.verifyEpoch(transactionContext, message.conversationId)
    .flatMap {
        syncManager.waitUntilLiveOrFailure().flatMap { attemptToSend(transactionContext, message) }
    }
```

CoreCrypto serialises transactions, and incoming event processing needs one too (`IncrementalSyncWorker.kt:80`). So for the duration of a send, up to 30 s of read timeout times up to six round trips, nothing else can decrypt, send or commit. In the stale-epoch path the sender waits for sync to become live while holding the lock that the sync worker needs to process events and become live. That resolves only when `waitUntilLiveOrFailure` observes a failure. The `attemptToSend` recursion at `:315` and `:330` also has no attempt counter.

Fix: split the flow into a short transaction to check sessions and encrypt, HTTP outside any transaction, and a new transaction on 412 to resolve clients and re-encrypt. Never call `waitUntilLive*` inside a transaction. Add a bounded attempt counter for the MLS out-of-sync loop.

### 6.2 Socket timeouts after the server accepted a send are treated as offline and resent (High)

`CoreFailureMappers.kt:93` maps any `GenericError` whose cause is an `IOException` to `NoNetworkConnection`, and `HttpResponseHandler.kt:101-108` catches every exception into `GenericError`. A read timeout that fires after the request body was delivered is therefore "no network", the message stays `PENDING`, and the next resend re-encrypts and re-posts the same id. Receivers dedupe with `INSERT OR IGNORE` (`Messages.sq:329-333`), so users do not see duplicates, but the crypto, bandwidth and server-side duplicate are real. Offline sends also hang for the full 30 s before failing.

Fix: distinguish connect failures from read timeouts after bytes were written, add a pre-flight `NetworkStateObserver` check, and install per-request timeouts (4.2).

### 6.3 No persistent retry with backoff; the scheduler is a no-op on iOS and JVM (Medium)

`MessageSendingScheduler` is an interface; the Android implementation fires a broadcast `Intent` (`androidMain/WorkSchedulerImpl.kt:91-106`) and the JVM and Apple ones log "not supported". Pending messages are re-sent only when the host app calls `sendPendingMessages`, sequentially, each holding the crypto transaction, and with `scheduleResendIfNoNetwork = false` so a second offline attempt flips them to `FAILED`. No attempt count, no next-attempt time, no expiry, no jitter.

Fix: a Kalium-owned retry queue with attempt count and next-attempt time persisted on the message, exponential backoff with jitter, expiry, per-conversation ordering and cross-conversation concurrency.

### 6.4 MLS commit retry regenerates the commit without resyncing (Medium)

`MLSConversationRepository.kt:296-329` maps `StaleMessage` and `ClientMismatch` to `Retry`, and `retryOperation()` (`:916-931`) re-runs the same lambda at the same local epoch. For add-member that means re-claiming key packages for every user (`:554`), which the backend consumes on each claim, and re-posting a commit that will be stale again. Nothing between attempts fetches group info or processes events, and nothing can while the transaction is held.

Fix: on stale, leave the transaction, process pending events or fetch group info and rejoin by external commit, then retry. Cache claimed key packages across retries.

### 6.5 Full conversation refetch on every Proteus client mismatch (Medium)

`MessageSendFailureHandler.kt:101-109` calls `fetchConversation` whenever `missing` or `deleted` is non-empty, alongside `fetchUsersByIds` and `listClientsOfUsers`. One new device on one member costs three metadata requests plus prekeys plus the resend, with the full conversation payload downloaded inside the transaction. Only refetch when `missing` names users not in the local member list.

### 6.6 Smaller sending items (Low)

- Key package claiming is one request per user, eight concurrent (`KeyPackageRepository.kt:118-138`). Adding 200 users is 200 requests in 25 waves. Group by domain and ask the backend for a bulk claim.
- Every message is sent with `nativePush = true`, `MessagePriority.HIGH`, `transient = false` (`MessageRepository.kt:516-522`), including receipts and edits. Mark signaling content that has no user-visible effect as transient with no native push; it cuts push traffic and notification stream size for every recipient.
- `MessageApiV0.kt:48-66` still carries a JSON and base64 `RequestBody` model that nothing uses. Delete it so nobody falls back to it.
- Every envelope does an `observeLegalHoldStatus(...).first()` DB read (`MessageEnvelopeCreator.kt:91-95`, `MLSMessageCreator.kt:95-99`). Pass it in or cache per conversation.

Receipts and typing are done properly: delivery receipts batched per conversation with a 1 s debounce, read receipts with a 3 s debounce through `ParallelConversationWorkQueue`, and typing throttled to one start and one stop per 10 s.

## 7. Assets

The pipeline is streamed end to end. Encrypt and decrypt go through okio `cipherSink` and `cipherSource`, upload is a `WriteChannelContent`, download copies the response channel into a file sink in 8 KiB chunks. Nothing holds a whole file in RAM. The cost is in the number of passes over disk and in the failure modes.

### 7.1 Upload replays an exhausted stream after a 401 (High)

`AssetRepository.kt:276-278`:

```kotlin
kaliumFileSystem.source(uploadAssetData.tempEncryptedDataPath).use { dataSource ->
    assetApi.uploadAsset(metaData, { dataSource }, uploadAssetData.dataSize)
```

The lambda returns the same `Source` instance on every call. When the access token expires mid-session, Ktor's `Auth` plugin refreshes and re-executes the request, which calls `StreamAssetContent.writeTo` (`AssetApiV0.kt:182-194`) again on an already-drained source. The body then contains the multipart headers and a part that declares `Content-Length: $encryptedDataSize` with no bytes behind it. The upload fails deterministically, and the user sees a failed attachment, precisely when the token was stale.

Fix: pass `{ kaliumFileSystem.source(path) }` so every `writeTo` opens a fresh source. The `use` inside `writeTo` already closes it.

### 7.2 Redundant disk passes on upload and download (Medium)

Upload for a file of size S: copy plaintext to a temp UUID name (`PersistNewAssetMessageUseCase.kt:36-42`, copy plus delete), encrypt to `.aes` (pass 1), SHA-256 of the ciphertext (pass 2, `AssetRepository.kt:202`), MD5 of the ciphertext (pass 3, `AssetMapper.kt:49-53`), stream to network (pass 4), then copy the plaintext again under the backend key (`AssetRepository.kt:284-299`). Roughly nine times S in disk I/O before and after the transfer. Download: stream to temp, SHA-256 pass, decrypt pass. Both cipher loops also `flush()` every 8 KiB (`AESUtils.kt:49,121`), a syscall per chunk, and the upload loop allocates a new `ByteArray` per chunk.

Fix: wrap the encrypt sink in `HashingSink.sha256(HashingSink.md5(fileSink))` to capture both digests in the encrypt pass; hash the download while it streams; use `atomicMove` instead of copy plus delete; flush once at the end; reuse a 64 KiB buffer.

### 7.3 Encrypt reports success on failure (Medium)

`AESUtils.kt:52-58` catches every exception, logs, and still returns `sizeWithPaddingAndIV(encryptedDataSize)`, which is at least 32 even when nothing was written. The caller only checks `encryptedDataSize > 0L` (`AssetRepository.kt:207`). A mid-stream I/O error produces a truncated `.aes` that is then hashed and uploaded with a wrong declared size; the failure surfaces later as a multipart mismatch from the backend. Return a sentinel or rethrow, and assert the file size equals the declared size before upload.

### 7.4 No `Content-Length` on uploads, no resume, no progress, no retry (Medium)

`StreamAssetContent` (`AssetApiV0.kt:157-162`) does not override `contentLength`, although the multipart size is fully computable, so uploads go out chunked and intermediaries cannot reject oversize bodies early. There is no `Range` usage anywhere, so an interrupted download restarts from zero; there is no byte-level progress callback, only status enum transitions; and there is no retry on `IOException` or 5xx. The Cells S3 client in `domain/cells` already has all three (known length, progress, three jittered attempts, multipart above 100 MiB) and is the reference to copy.

### 7.5 No concurrency limit and no in-flight dedup for downloads (Medium)

`GetMessageAssetUseCase.kt:56` launches an independent `async` per call and only checks the DB after the download completes (`AssetRepository.kt:343-347`). Two rapid calls for the same message trigger two full downloads and two decrypts to the same path; a conversation full of images spawns N parallel downloads bounded only by OkHttp's `maxRequestsPerHost`. Keep an in-flight `Map<assetId, Deferred>` behind a mutex and bound parallel downloads with a small semaphore.

### 7.6 Smaller asset items (Low)

- Cancelling a message after `uploadAsset` returned leaves an orphaned asset on the backend with `PERSISTENT` retention (`ScheduleNewAssetMessageUseCase.kt:55-68`), and a resend after a client-side failure post-upload creates a second asset. Persist the uploaded key before message persistence, and delete on cancel.
- `CellUploadManagerImpl.kt:74-82` launches a coroutine per 8 KiB progress tick and mutates a plain `mutableMapOf` from several coroutines. Throttle progress to 1 percent or 256 KiB deltas, and use a `MutableStateFlow` or a mutex.
- The `HttpTrafficObserver` interceptor (`commonJvmAndroid/HttpEngine.kt:121-163`) reads whole request and response bodies into memory and decodes gzip itself. It is opt-in and only wired on JVM and Android, but nothing stops a production build enabling it. Skip bodies for streaming content types or above a size cap.

## 8. Slow sync and bootstrap

Steps run strictly sequentially through an `Either` chain in `SlowSyncWorker.kt:61-109`. For N conversations, M known users, T team members and C connections the budget is roughly: 4 single requests, 2 times N/1000 for conversations, C/500 for connections plus one extra per pending outgoing connection, 1 plus T/200 for the team, M/500 plus team-member lookups for contacts, then MLS joins and 1:1 resolution at four concurrent. Conversations and users are well batched. The problems are the shape of the retry and a few N+1s.

### 8.1 Slow sync is not resumable (High)

Any `Left` throws `KaliumSyncException` (`SlowSyncWorker.kt:107-109`), `SlowSyncManager.kt:84-103` schedules a full `doSync()` after backoff, and completion is recorded only at the end. `SlowSyncStatus.Ongoing(step)` is emitted but never read back. `fetchMembersByTeamId` restarts its paging state from `null` each time (`TeamRepository.kt:122`). On a 20,000 member team a transient 5xx in the contacts step repeats the 100-page member walk, then waits up to 10 min.

Fix: persist the last completed `SlowSyncStep` plus paging state for the conversations, team and contacts steps in `SlowSyncRepository`, and resume from there.

### 8.2 One conversation fetch per pending outgoing connection (High)

`ConnectionRepository.kt:281-296` handles `SENT` connections by calling `conversationRepository.fetchConversation(...)` one at a time, sequentially, inside the per-page loop. An account with a few hundred outgoing requests (sales and support users) adds a few hundred round trips to every slow sync. Collect the ids across pages and issue one `POST /conversations/list/v2`, which the main conversation step already uses, then override the type to `WAIT_FOR_CONNECTION` on the batch.

### 8.3 Contacts step refetches every known user every time (Medium)

`UserRepository.kt:238-243` fetches `allOtherUsersId()` in 500-id chunks on every slow sync regardless of staleness, and `persistUsers` calls `legalHoldHandler.handleUserFetch` per user (`:390-393`), which on a status change issues one `/users/list-clients` per changed user. The accumulating `.distinct()` on `usersFound` (`:281-284`) is quadratic in the number of pages. For 10,000 known users that is 20 requests with 500-id bodies and 20 upserts of 500 rows, every time. Refetch only users older than a TTL or referenced by conversations fetched in this sync, and batch the legal-hold client refresh.

### 8.4 Enabling the Meetings flag triggers a full slow sync fleet-wide (Medium)

`MeetingsConfigHandler.kt:36-39` calls `clearLastSlowSyncCompletionInstant()` when the flag flips on. Every client in the team re-runs every step at once, purely to execute one optional `syncMeetings()` step. Call `syncMeetings()` directly from the handler.

### 8.5 Team member walk at 200 per page with no default limit (Medium)

`FETCH_TEAM_MEMBER_PAGE_SIZE = 200` (`TeamRepository.kt:77`) and `limitTeamMembersFetchDuringSlowSync` is null by default. A 50,000 member team is 250 sequential requests, each followed by an upsert, and all of it repeated by 8.1. The backend allows larger pages. Raise the page size, set a sensible default limit and rely on on-demand `fetchUsersIfUnknownByIds` for the tail.

### 8.6 One SQLite transaction per conversation for members (Medium)

`ConversationRepository.kt:485-503` loops conversations and calls `memberDAO.updateFullMemberList` or `insertMembersWithQualifiedId` per conversation, each its own `withContext(writeDispatcher) { transaction { ... } }`. A page of 1000 conversations is 1000 write transactions, each an fsync on mobile flash, while the network sits idle. `PersistConversationsUseCase.kt:34-46` also calls `selfTeamIdProvider()` per conversation. Add a `replaceMembersForConversations(Map)` DAO method that runs once per page.

### 8.7 Per-event conversation fetch on `MemberJoin` (Medium)

`MemberJoinEventHandler.kt:74` calls `fetchConversation` unconditionally, although the comment above it lists three narrow cases. After a long offline period, K member-join events cost K sequential fetches, refetching the same conversation for each join. Fetch only when self was added, the member is a bot, or the conversation is unknown locally; otherwise persist members from the event.

### 8.8 Smaller sync items (Low)

- `/users/list-clients` is never chunked (`ClientRemoteRepository.kt:102-107`, `ConversationRepository.kt:769-771`, `MLSConversationRepository.kt:612`). Conversations above roughly 500 to 1000 members risk a 400 that blocks sending and calling. Chunk at 500 and merge.
- `JoinExistingMLSConversationsUseCase.kt:59-70` does one DB query per pending conversation to check self membership. One query with an id list.
- `GET /api-version` runs on every foreground for every server config. Cheap, but the negotiated version is only applied when the session scope is rebuilt.

## 9. Logging and observability

### 9.1 Response headers are logged unredacted (High, security rather than performance)

`KaliumHttpLogger.kt:66` runs request headers through `obfuscatedJsonMessage`. `KaliumHttpLogger.kt:81` stores the response headers as a plain map and `closeResponseLog` logs them verbatim at VERBOSE. `sensitiveJsonKeys` lists `set-cookie` but is only applied on the request path. With request logging enabled, the `Set-Cookie: zuid=...` refresh token from `/access` and `/login` lands in the log. The `HttpTrafficObserver` KDoc claims the logger is safe to enable in production, which is not true for responses. Apply the same redaction on the response path.

### 9.2 Eager verbose formatting on the websocket hot path (Medium, trivial fix)

`NotificationApiV9.kt:135` and `NotificationApiV0.kt:170`:

```kotlin
logger.v("Binary frame content: '${deleteSensitiveItemsFromJson(jsonString)}'")
val event = KtxSerializer.json.decodeFromString<ConsumableNotificationResponse>(jsonString)
```

`KaliumLogger.v` takes a `String`, so the template is evaluated before the severity check. `deleteSensitiveItemsFromJson` (`ObfuscateUtil.kt:156-178`) parses the whole frame into a `JsonElement` tree and builds a string by concatenation. Every incoming event is therefore JSON-parsed twice, and the first parse is discarded at any log level above VERBOSE. Gate it on `logger.logLevel` or add lambda-taking overloads to `KaliumLogger`.

When request logging is enabled, `KaliumHttpLogger` also serialises the header map to a string and parses it back to redact it (`:66`), re-parses the URL, and allocates two `Job`s per request. When disabled the plugin is not installed at all, which is the right default.

### 9.3 Link preview client bypasses the configured SOCKS proxy (Medium)

`MessageScope.kt:621` builds the link preview client with a bare `HttpClient { ... }`, so it uses the platform default engine, not `defaultHttpEngine` with the session's `ApiProxy` and pinning. On a deployment that requires the SOCKS proxy, link preview fetches of arbitrary user-pasted URLs go direct and reveal the client's IP to the link host. The tight timeouts and `followRedirects = false` are good. Either route it through the same proxy configuration or disable previews when a proxy is required.

## 10. What is done well

- HTTP/2 with `RESTRICTED_TLS`, certificate pinning on OkHttp and Darwin, gzip by default with a dedicated no-compression client for assets.
- Single-flight token refresh via Ktor's `AuthTokenHolder`, one provider shared by REST and websocket clients, refresh cookie parsed and rotated correctly.
- Protobuf envelope through a pass-through converter, batched Proteus encryption, local recipient lists, one batched qualified prekey request, external messages above 200 KiB.
- `message/mls` for messages and commit bundles, commit-bundle events applied locally from the response, pending-proposal commits jittered via persisted timers.
- Conversation bootstrap at 1000 per page with `list-ids` plus `list/v2`, users at 500 per chunk with federation fallback, MLS joins and 1:1 resolution bounded to four concurrent with 429 awareness.
- Receipts batched and debounced, typing throttled, receiver-side dedupe with `INSERT OR IGNORE`.
- Streamed asset encryption and transfer, assets uploaded before the message is sent so recipients never receive an unfetchable asset.
- Sync backoff waits for reconnection rather than sleeping blindly; `SelfUserDeleted` short-circuits to logout.
- The Cells S3 client is a solid reference implementation for known-length streaming, progress, jittered retry and multipart.

## 11. Suggested order of work

Quick wins (each a small PR with a unit test):

1. `nonCancellableRefresh = true` and `NonCancellable` around token persistence (4.1).
2. Fresh `Source` per `writeTo` in asset upload (7.1); sentinel return from `encryptFile` (7.3).
3. Add `engine = engine` to the v17 branch (4.4).
4. Redact response headers (9.1); gate the websocket verbose log (9.2); close the disposable websocket client and fix the empty-page cursor (5.4, 5.7).
5. Jitter in `ExponentialDurationHelper` (4.2).
6. Call `syncMeetings()` directly instead of invalidating slow sync (8.4).
7. Batch `SENT` connection conversations (8.2); chunk `list-clients` (8.8).

Medium:

8. `HttpTimeout` and `HttpRequestRetry` with `Retry-After` on the base client (4.2).
9. Parse error bodies once (4.5).
10. Hash while encrypting, `atomicMove`, `Content-Length` on uploads (7.2, 7.4).
11. Slow sync checkpoints and paging state (8.1); per-page member transactions (8.6).
12. Sub-batch event processing with a poison-event policy, batched acks with `multiple = true`, larger legacy page size (5.2, 5.3, 5.6).
13. Conditional `fetchConversation` on `MemberJoin` and on mismatch (8.7, 6.5).

Larger:

14. Move HTTP out of the CoreCrypto transaction and bound the MLS retry loop (6.1, 6.4).
15. A Kalium-owned persistent send queue with backoff, expiry and per-conversation ordering (6.3).
16. Resume, progress and retry for Wire assets, modelled on the Cells client (7.4, 7.5).
