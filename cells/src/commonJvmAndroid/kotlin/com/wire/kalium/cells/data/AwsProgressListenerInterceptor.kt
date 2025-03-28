/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */
package com.wire.kalium.cells.data

import aws.smithy.kotlin.runtime.client.ProtocolRequestInterceptorContext
import aws.smithy.kotlin.runtime.client.ProtocolResponseInterceptorContext
import aws.smithy.kotlin.runtime.http.HttpBody
import aws.smithy.kotlin.runtime.http.interceptors.HttpInterceptor
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.http.request.toBuilder
import aws.smithy.kotlin.runtime.http.response.HttpResponse
import aws.smithy.kotlin.runtime.io.SdkBuffer
import aws.smithy.kotlin.runtime.io.SdkByteReadChannel
import aws.smithy.kotlin.runtime.io.SdkSource
import aws.smithy.kotlin.runtime.io.readAll

internal open class AwsProgressListenerInterceptor(
    private val progressListener: (Long) -> Unit
) : HttpInterceptor {
    fun convertBodyWithProgressUpdates(httpBody: HttpBody): HttpBody {
        return when (httpBody) {
            is HttpBody.ChannelContent -> {
                SdkByteReadChannelWithProgressUpdates(
                    httpBody,
                    progressListener
                )
            }
            is HttpBody.SourceContent -> {
                SourceContentWithProgressUpdates(
                    httpBody,
                    progressListener
                )
            }
            is HttpBody.Bytes -> {
                httpBody
            }
            is HttpBody.Empty -> {
                httpBody
            }
        }
    }

    internal class SourceContentWithProgressUpdates(
        private val sourceContent: SourceContent,
        private val progressListener: (Long) -> Unit
    ) : HttpBody.SourceContent() {
        private val delegate = sourceContent.readFrom()
        private var uploaded = 0L
        override val contentLength: Long?
            get() = sourceContent.contentLength

        override fun readFrom(): SdkSource {
            return object : SdkSource {
                override fun close() {
                    delegate.close()
                }

                override fun read(sink: SdkBuffer, limit: Long): Long {
                    return delegate.read(sink, limit).also {
                        if (it > 0) {
                            uploaded += it
                            progressListener(uploaded)
                        }
                    }
                }
            }
        }
    }

    internal class SdkByteReadChannelWithProgressUpdates(
        private val httpBody: ChannelContent,
        private val progressListener: (Long) -> Unit
    ) : HttpBody.ChannelContent() {
        val delegate = httpBody.readFrom()
        private var uploaded = 0L
        override val contentLength: Long?
            get() = httpBody.contentLength
        override fun readFrom(): SdkByteReadChannel {
            return object : SdkByteReadChannel by delegate {
                override val availableForRead: Int
                    get() = delegate.availableForRead

                override val isClosedForRead: Boolean
                    get() = delegate.isClosedForRead

                override val isClosedForWrite: Boolean
                    get() = delegate.isClosedForWrite

                override fun cancel(cause: Throwable?): Boolean {
                    return delegate.cancel(cause)
                }

                override suspend fun read(sink: SdkBuffer, limit: Long): Long {
                    return delegate.readAll(sink).also {
                        if (it > 0) {
                            uploaded += it
                            progressListener(uploaded)
                        }
                    }
                }
            }
        }
    }

    internal class DownloadProgressListenerInterceptor(
        progressListener: (Long) -> Unit
    ) : AwsProgressListenerInterceptor(progressListener) {
        override suspend fun modifyBeforeDeserialization(
            context: ProtocolResponseInterceptorContext<Any, HttpRequest, HttpResponse>
        ): HttpResponse {
            val body = convertBodyWithProgressUpdates(context.protocolResponse.body)
            return HttpResponse(context.protocolResponse.status, context.protocolResponse.headers, body)
        }
    }

    internal class UploadProgressListenerInterceptor(
        progressListener: (Long) -> Unit
    ) : AwsProgressListenerInterceptor(progressListener) {
        override suspend fun modifyBeforeTransmit(
            context: ProtocolRequestInterceptorContext<Any, HttpRequest>
        ): HttpRequest {
            val builder = context.protocolRequest.toBuilder()
            builder.body = convertBodyWithProgressUpdates(builder.body)
            return builder.build()
        }
    }
}
