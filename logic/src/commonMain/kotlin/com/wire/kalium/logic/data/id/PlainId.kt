package com.wire.kalium.logic.data.id

import kotlin.jvm.JvmInline

@JvmInline
value class PlainId(val value: String)

@JvmInline
value class CacheFolder(val value: String)

@JvmInline
value class AssetsStorageFolder(val value: String)

typealias TeamId = PlainId
