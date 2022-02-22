package com.wire.kalium.logic.data.message

import kotlin.jvm.JvmInline

/**
 * [EncryptedMessageBlob] and [PlainMessageBlob] wrappers exist to avoid confusion and provide an easier to use development environment.
 * Functions like `encrypt` can take a [PlainMessageBlob] as input and output an [EncryptedMessageBlob].
 * And a `decrypt` function could do the reverse.
 */
@JvmInline
value class EncryptedMessageBlob(val data: ByteArray)

/**
 * [EncryptedMessageBlob] and [PlainMessageBlob] wrappers exist to avoid confusion and provide an easier to use development environment.
 * Functions like `encrypt` can take a [PlainMessageBlob] as input and output an [EncryptedMessageBlob].
 * And a `decrypt` function could do the reverse.
 */
@JvmInline
value class PlainMessageBlob(val data: ByteArray)
