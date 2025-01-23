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
package com.wire.cells.s3

import aws.sdk.kotlin.runtime.auth.credentials.StaticCredentialsProvider
import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.completeMultipartUpload
import aws.sdk.kotlin.services.s3.createMultipartUpload
import aws.sdk.kotlin.services.s3.deleteObject
import aws.sdk.kotlin.services.s3.listObjectsV2
import aws.sdk.kotlin.services.s3.model.CompletedMultipartUpload
import aws.sdk.kotlin.services.s3.model.CompletedPart
import aws.sdk.kotlin.services.s3.uploadPart
import aws.smithy.kotlin.runtime.auth.awscredentials.Credentials
import aws.smithy.kotlin.runtime.content.ByteStream
import aws.smithy.kotlin.runtime.net.url.Url
import okio.Path
import java.io.RandomAccessFile
import java.nio.ByteBuffer

actual fun cellsS3Client(credentials: S3ClientCredentials): CellsS3Client = CellsS3ClientJvm(credentials)

private class CellsS3ClientJvm(private val credentials: S3ClientCredentials) : CellsS3Client {

    private val s3Client: S3Client by lazy { buildS3Client() }

    private fun buildS3Client() = with(credentials) {
        S3Client {
            region = regionName
            credentialsProvider = StaticCredentialsProvider(
                Credentials(
                    accessKeyId = accessToken,
                    secretAccessKey = gatewaySecret,
                )
            )
            endpointUrl = Url.parse(serverUrl)
        }
    }

    override suspend fun list(cellName: String) =
        withS3Client {
            listObjectsV2 {
                bucket = credentials.bucketName
                prefix = cellName
            }.contents?.mapNotNull { it.key } ?: emptyList()
        }

    override suspend fun upload(cellName: String, fileName: String, path: Path, onProgressUpdate: (Long) -> Unit) {

        val buffer = ByteBuffer.allocate(5 * 1024 * 1024)
        var number = 1
        val completed = mutableListOf<CompletedPart>()

        withS3Client {

            val requestId = createMultipartUpload {
                bucket = credentials.bucketName
                key = "$cellName/$fileName"
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
                        bucket = credentials.bucketName
                        key = "$cellName/$fileName"
                        uploadId = requestId
                        partNumber = number
                        contentLength = bytesRead.toLong()
                        body = ByteStream.fromBytes(partData)
                    }

                    completed.add(CompletedPart {
                        partNumber = number
                        eTag = response.eTag
                    })

                    buffer.clear()
                    position += bytesRead
                    number++
                }
            }

            completeMultipartUpload {
                bucket = credentials.bucketName
                key = "$cellName/$fileName"
                uploadId = requestId
                multipartUpload = CompletedMultipartUpload {
                    parts = completed
                }
            }
        }
    }

    override suspend fun delete(cellName: String, fileName: String) {
        withS3Client {
            deleteObject {
                bucket = credentials.bucketName
                key = "$cellName/$fileName"
            }
        }
    }

    private inline fun <T> withS3Client(block: S3Client.() -> T): T {
        return with(s3Client) { block() }
    }
}
