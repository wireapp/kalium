package com.wire.kalium.network.http.extension

import okhttp3.Headers

internal fun Headers.fromOkHttp(): io.ktor.http.Headers = object : io.ktor.http.Headers {
    override val caseInsensitiveName: Boolean = true

    override fun getAll(name: String): List<String>? = this@fromOkHttp.values(name).takeIf { it.isNotEmpty() }

    override fun names(): Set<String> = this@fromOkHttp.names()

    override fun entries(): Set<Map.Entry<String, List<String>>> = this@fromOkHttp.toMultimap().entries

    override fun isEmpty(): Boolean = this@fromOkHttp.size == 0
}
