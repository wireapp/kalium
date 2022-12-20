package com.wire.kalium.network.http.extension

import io.ktor.http.HttpProtocolVersion
import okhttp3.Protocol

@Suppress("DEPRECATION")
internal fun Protocol.fromOkHttp(): HttpProtocolVersion = when (this) {
    Protocol.HTTP_1_0 -> HttpProtocolVersion.HTTP_1_0
    Protocol.HTTP_1_1 -> HttpProtocolVersion.HTTP_1_1
    Protocol.SPDY_3 -> HttpProtocolVersion.SPDY_3
    Protocol.HTTP_2 -> HttpProtocolVersion.HTTP_2_0
    Protocol.H2_PRIOR_KNOWLEDGE -> HttpProtocolVersion.HTTP_2_0
    Protocol.QUIC -> HttpProtocolVersion.QUIC
}
