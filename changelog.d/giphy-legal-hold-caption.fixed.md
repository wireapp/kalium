`sendGifMessage` no longer sends the GIF when its caption fails because sending found the conversation under legal hold. It returns `SendGifMessageResult.Failure` with `LegalHoldEnabledForConversationFailure`, whose `messageId` is the stored caption. Once the user accepts legal hold, retry that caption with `retryFailedMessage` and send the GIF again without a caption. A caption that fails for any other reason after it was stored is still followed by the GIF.

  - ABI: unchanged.
  - Source: unchanged.
  - Migration: hosts that treated every `SendGifMessageResult.Failure` as "nothing was stored" should retry the caption instead of sending it again when the failure is `LegalHoldEnabledForConversationFailure`.
