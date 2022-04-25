package com.wire.kalium.logic.util

import java.text.SimpleDateFormat
import java.util.Date

private val serverDateTimeFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")

fun String.toTimeInMillis(): Long = serverDateTimeFormat.parse(this).time

fun Long.toStringDate() : String = serverDateTimeFormat.format(Date(this))
