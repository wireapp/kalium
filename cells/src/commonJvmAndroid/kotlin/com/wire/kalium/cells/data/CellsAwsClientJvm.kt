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

import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.completeMultipartUpload
import aws.sdk.kotlin.services.s3.createMultipartUpload
import aws.sdk.kotlin.services.s3.model.CompletedMultipartUpload
import aws.sdk.kotlin.services.s3.model.CompletedPart
import aws.sdk.kotlin.services.s3.model.GetObjectRequest
import aws.sdk.kotlin.services.s3.presigners.presignGetObject
import aws.sdk.kotlin.services.s3.putObject
import aws.sdk.kotlin.services.s3.uploadPart
import aws.sdk.kotlin.services.s3.withConfig
import aws.smithy.kotlin.runtime.content.ByteStream
import aws.smithy.kotlin.runtime.content.fromFile
import aws.smithy.kotlin.runtime.content.toInputStream
import aws.smithy.kotlin.runtime.net.url.Url
import com.wire.kalium.cells.data.model.CellNodeDTO
import com.wire.kalium.cells.domain.model.CellsCredentials
import com.wire.kalium.network.api.base.authenticated.AccessTokenApi
import com.wire.kalium.network.session.SessionManager
import okhttp3.internal.http2.Header
import okio.Path
import okio.Sink
import okio.buffer
import okio.source
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import kotlin.time.Duration.Companion.hours

internal actual fun cellsAwsClient(
    credentials: CellsCredentials?,
    sessionManager: SessionManager,
    accessTokenApi: AccessTokenApi
): CellsAwsClient = CellsAwsClientJvm(credentials, sessionManager, accessTokenApi)

private class CellsAwsClientJvm(
    private val credentials: CellsCredentials?,
    private val sessionManager: SessionManager,
    private val accessTokenAPI: AccessTokenApi,
) : CellsAwsClient {

    private companion object {
        const val DEFAULT_REGION = "us-east-1"
        const val DEFAULT_BUCKET_NAME = "io"
        const val MAX_REGULAR_UPLOAD_SIZE = 100 * 1024 * 1024L
        const val MULTIPART_CHUNK_SIZE = 10 * 1024 * 1024
    }

    private val s3Client: S3Client by lazy { buildS3Client() }

    private fun buildS3Client() = with(credentials) {
        S3Client {
            region = DEFAULT_REGION
            enableAwsChunked = true
            endpointUrl = Url.parse(this@with?.serverUrl ?: "")
            credentialsProvider = TokenRefreshingCredentialsProvider(sessionManager, accessTokenAPI, this@with?.gatewaySecret ?: "")
        }
    }

    override suspend fun download(objectKey: String, outFileSink: Sink, onProgressUpdate: (Long) -> Unit) {
        withS3Client(downloadProgressListener = onProgressUpdate) {
            getObject(
                GetObjectRequest {
                    bucket = DEFAULT_BUCKET_NAME
                    key = objectKey
                }
            ) { response ->
                response.body?.toInputStream()?.let { input ->
                    outFileSink.buffer().use { out ->
                        out.writeAll(input.source())
                    }
                }
            }
        }
    }

    override suspend fun upload(path: Path, node: CellNodeDTO, onProgressUpdate: (Long) -> Unit) {
        val length = path.toFile().length()
        if (length > MAX_REGULAR_UPLOAD_SIZE) {
            uploadMultipart(path, node, onProgressUpdate)
        } else {
            uploadRegular(path, node, onProgressUpdate)
        }
    }

    private suspend fun uploadRegular(path: Path, node: CellNodeDTO, onProgressUpdate: (Long) -> Unit) {
        withS3Client(uploadProgressListener = onProgressUpdate) {
            putObject {
                bucket = DEFAULT_BUCKET_NAME
                key = node.path
                metadata = node.createDraftNodeMetaData()
                body = ByteStream.fromFile(path.toFile())
                contentLength = path.toFile().length()
            }
        }
    }

    private suspend fun uploadMultipart(path: Path, node: CellNodeDTO, onProgressUpdate: (Long) -> Unit) {
        val buffer = ByteBuffer.allocate(MULTIPART_CHUNK_SIZE)
        var number = 1
        val completed = mutableListOf<CompletedPart>()
        withS3Client {
            val requestId = createMultipartUpload {
                bucket = DEFAULT_BUCKET_NAME
                key = node.path
                metadata = node.createDraftNodeMetaData()
            }.uploadId
            RandomAccessFile(path.toFile(), "r").use { file ->
                val fileSize = file.length()
                var position = 0L
                while (position < fileSize) {
                    file.seek(position)
                    val bytesRead = file.channel.read(buffer)
                    onProgressUpdate(position + bytesRead)
                    buffer.flip()
                    val partData = ByteArray(bytesRead)
                    buffer.get(partData, 0, bytesRead)
                    val response = uploadPart {
                        bucket = DEFAULT_BUCKET_NAME
                        key = node.path
                        uploadId = requestId
                        partNumber = number
                        contentLength = bytesRead.toLong()
                        body = ByteStream.fromBytes(partData)
                    }
                    completed.add(
                        CompletedPart {
                            partNumber = number
                            eTag = response.eTag
                        }
                    )
                    buffer.clear()
                    position += bytesRead
                    number++
                }
            }
            completeMultipartUpload {
                bucket = DEFAULT_BUCKET_NAME
                key = node.path
                uploadId = requestId
                multipartUpload = CompletedMultipartUpload {
                    parts = completed
                }
            }
        }
    }

    override suspend fun getPreSignedUrl(objectKey: String): String =
        withS3Client {
            val request = GetObjectRequest {
                key = objectKey
            }
            val preSignedRequest = presignGetObject(request, 24.hours)
            preSignedRequest.url.toString()
        }

    private suspend fun <T> withS3Client(
        uploadProgressListener: ((Long) -> Unit)? = null,
        downloadProgressListener: ((Long) -> Unit)? = null,
        block: suspend S3Client.() -> T,
    ): T =
        s3Client.withConfig {
            if (uploadProgressListener != null) {
                Header.TARGET_PATH
                interceptors.add(AwsProgressListenerInterceptor.UploadProgressListenerInterceptor(uploadProgressListener))
                interceptors.add(RemoveExpectInterceptor())
            }
            if (downloadProgressListener != null) {
                interceptors.add(AwsProgressListenerInterceptor.DownloadProgressListenerInterceptor(downloadProgressListener))
            }
        }.use {
            block(it)
        }
}

private fun CellNodeDTO.createDraftNodeMetaData() = mapOf(
    MetadataHeaders.DRAFT_MODE to "true",
    MetadataHeaders.CREATE_RESOURCE_UUID to uuid,
    MetadataHeaders.CREATE_VERSION_ID to versionId,
)
