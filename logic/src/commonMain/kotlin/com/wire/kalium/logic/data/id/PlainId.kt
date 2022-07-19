package com.wire.kalium.logic.data.id

import kotlin.jvm.JvmInline

@JvmInline
value class PlainId(val value: String)

typealias TeamId = PlainId
