package com.wire.kalium.logic.util

import java.text.SimpleDateFormat

private val serverDateTimeFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")

fun String.toTimeInMillis(): Long = serverDateTimeFormat.parse(this).time
