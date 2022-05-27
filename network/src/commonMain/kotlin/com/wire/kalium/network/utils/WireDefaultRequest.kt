package com.wire.kalium.network.utils

import com.wire.kalium.network.ServerMetaDataManager
import com.wire.kalium.network.api.versioning.VersionApiImpl
import com.wire.kalium.network.defaultHttpEngine
import com.wire.kalium.network.kaliumLogger
import com.wire.kalium.network.provideBaseHttpClient
import com.wire.kalium.network.shouldAddApiVersion
import com.wire.kalium.network.tools.ServerConfigDTO
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpClientPlugin
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.HttpRequestPipeline
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.HeadersBuilder
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMessageBuilder
import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import io.ktor.http.encodedPath
import io.ktor.http.set
import io.ktor.http.takeFrom
import io.ktor.util.AttributeKey
import io.ktor.util.Attributes
import io.ktor.util.appendAll


/**
 * Sets default request parameters. Used to add common headers and URL for a request.
 * Note that trailing slash in base URL and leading slash in request URL are important.
 * The rules to calculate a final URL:
 * 1. Request URL doesn't start with slash
 *     * Base URL ends with slash ->
 *       concat strings.
 *       Example:
 *       base = `https://example.com/dir/`,
 *       request = `file.html`,
 *       result = `https://example.com/dir/file.html`
 *     * Base URL doesn't end with slash ->
 *       remove last path segment of base URL and concat strings.
 *       Example:
 *       base = `https://example.com/dir/deafult_file.html`,
 *       request = `file.html`,
 *       result = `https://example.com/dir/file.html`
 * 2. Request URL starts with slash -> use request path as is.
 *   Example:
 *   base = `https://example.com/dir/deafult_file.html`,
 *   request = `/root/file.html`,
 *   result = `https://example.com/root/file.html`
 *
 * Headers of the builder will be pre-populated with request headers.
 * You can use [HeadersBuilder.contains], [HeadersBuilder.appendIfNameMissing]
 * and [HeadersBuilder.appendIfNameAndValueMissing] to avoid appending some header twice.
 *
 * Usage:
 * ```
 * val client = HttpClient {
 *   defaultRequest {
 *     url("https://base.url/dir/")
 *     headers.appendIfNameMissing(HttpHeaders.ContentType, ContentType.Application.Json)
 *   }
 * }
 * client.get("file")
 *   // <- requests "https://base.url/dir/file", ContentType = Application.Json
 * client.get("/other_root/file")
 *   // <- requests "https://base.url/other_root/file", ContentType = Application.Json
 * client.get("//other.host/path")
 *   // <- requests "https://other.host/path", ContentType = Application.Json
 * client.get("https://some.url") { HttpHeaders.ContentType = ContentType.Application.Xml }
 *   // <- requests "https://some.url/", ContentType = Application.Xml
 * ```
 */
class WireDefaultRequest private constructor(var provider: WireServerMetaDataConfig = WireServerMetaDataConfig()) {

    companion object Plugin : HttpClientPlugin<WireDefaultRequest, WireDefaultRequest> {
        override val key: AttributeKey<WireDefaultRequest> = AttributeKey("WireDefaultRequest")

        override fun prepare(block: WireDefaultRequest.() -> Unit): WireDefaultRequest = WireDefaultRequest().apply(block)

        override fun install(plugin: WireDefaultRequest, scope: HttpClient) {
            var serverConfigDTO: ServerConfigDTO? = null
            scope.requestPipeline.intercept(HttpRequestPipeline.Before) {
                if (serverConfigDTO == null) {
                    serverConfigDTO = plugin.provider.loadServerData() ?: plugin.provider.fetchAndStoreMetadata() ?: return@intercept
                }

                val defaultRequest = WireDefaultRequestBuilder().apply {
                    headers.appendAll(context.headers)
                    serverConfigDTO?.let {
                        plugin.provider.buildDefaultRequest(this, it)
                    }?: return@intercept
                }
                val defaultUrl = defaultRequest.url.build()
                if (context.url.host.isEmpty()) {
                    mergeUrls(defaultUrl, context.url)
                }

                defaultRequest.attributes.allKeys.forEach {
                    if (!context.attributes.contains(it)) {
                        @Suppress("UNCHECKED_CAST") context.attributes.put(it as AttributeKey<Any>, defaultRequest.attributes[it])
                    }
                }
                context.headers.appendMissing(defaultRequest.headers.build())
            }
        }

        private fun mergeUrls(baseUrl: Url, requestUrl: URLBuilder) {
            val url = URLBuilder(baseUrl)
            with(requestUrl) {
                if (encodedPathSegments.size > 1 && encodedPathSegments.first().isEmpty()) {
                    // path starts from "/"
                    url.encodedPathSegments = encodedPathSegments
                } else if (encodedPathSegments.size != 1 || encodedPathSegments.first().isNotEmpty()) {
                    url.encodedPathSegments = url.encodedPathSegments.dropLast(1) + encodedPathSegments
                }
                url.encodedFragment = encodedFragment
                url.encodedParameters = encodedParameters
                takeFrom(url)
            }
        }
    }

    /**
     * Configuration object for [WireDefaultRequestBuilder] plugin
     */
    class WireDefaultRequestBuilder internal constructor() : HttpMessageBuilder {

        override val headers: HeadersBuilder = HeadersBuilder()
        val url: URLBuilder = URLBuilder()
        val attributes: Attributes = Attributes(concurrent = true)

        /**
         * Executes a [block] that configures the [URLBuilder] associated to this request.
         */
        fun url(block: URLBuilder.() -> Unit): Unit = block(url)

        /**
         * Sets the [url] using the specified [scheme], [host], [port] and [path].
         * Pass `null` to keep existing value in the [URLBuilder].
         */
        fun url(
            scheme: String? = null, host: String? = null, port: Int? = null, path: String? = null, block: URLBuilder.() -> Unit = {}
        ) {
            url.set(scheme, host, port, path, block)
        }

        /**
         * Sets the [HttpRequestBuilder.url] from [urlString].
         */
        fun url(urlString: String) {
            url.takeFrom(urlString)
        }

        /**
         * Gets the associated URL's host.
         */
        var host: String
            get() = url.host
            set(value) {
                url.host = value
            }

        /**
         * Gets the associated URL's port.
         */
        var port: Int
            get() = url.port
            set(value) {
                url.port = value
            }

        /**
         * Sets attributes using [block].
         */
        fun setAttributes(block: Attributes.() -> Unit) {
            attributes.apply(block)
        }
    }
}


class WireServerMetaDataConfig {
    internal var fetchAndStoreMetadata: suspend () -> ServerConfigDTO? = { null }
    internal var loadServerData: suspend () -> ServerConfigDTO? = { null }
    internal var buildDefaultRequest: WireDefaultRequest.WireDefaultRequestBuilder.(wireServer: ServerConfigDTO) -> Unit = { }
}

fun WireDefaultRequest.config(block: () -> WireServerMetaDataConfig) {
    provider = block()
}

fun HttpClientConfig<*>.installWireDefaultRequest(
    backendLinks: ServerConfigDTO.Links,
    serverMetaDataManager: ServerMetaDataManager
) {
    install(WireDefaultRequest) {
        config {
            WireServerMetaDataConfig().apply {
                loadServerData = {
                    serverMetaDataManager.getLocalMetaData(backendLinks).also {
                        kaliumLogger.d("WireDefaultRequest: loading server config from local: $it")
                    }
                }

                fetchAndStoreMetadata = {
                    val versionApi = VersionApiImpl(provideBaseHttpClient(defaultHttpEngine()))
                    when (val result = versionApi.fetchApiVersion(backendLinks.api)) {
                        is NetworkResponse.Success ->
                            serverMetaDataManager.storeBackend(
                                backendLinks, result.value
                            )
                        is NetworkResponse.Error -> null
                    }.also {
                        kaliumLogger.d("WireDefaultRequest: loading server config from remote: $it")
                    }
                }

                buildDefaultRequest = {
                    header(HttpHeaders.ContentType, ContentType.Application.Json)
                    with(it) {
                        val apiBaseUrl = links.api
                        // enforce https as url protocol
                        url.protocol = URLProtocol.HTTPS
                        // add the default host
                        url.host = apiBaseUrl.host
                        // for api version 0 no api version should be added to the request
                        url.encodedPath =
                            if (shouldAddApiVersion(metaData.commonApiVersion.version))
                                apiBaseUrl.encodedPath + "v${metaData.commonApiVersion.version}/"
                            else apiBaseUrl.encodedPath
                    }
                }
            }
        }
    }
}
