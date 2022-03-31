package com.wire.kalium.calling.callbacks

import com.sun.jna.Callback
import com.sun.jna.Pointer

fun interface MetricsHandler : Callback {
    fun onMetricsReady(conversationId: String, metricsJson: String, arg: Pointer?)
}
