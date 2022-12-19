package com.wire.kalium.network

import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import okhttp3.MediaType
import okhttp3.RequestBody
import okio.BufferedSink
import okio.IOException
import okio.source
import okio.use
import kotlin.coroutines.CoroutineContext

class ByteChannelRequestBody(
    private val contentLength: Long?,
    private val callContext: CoroutineContext,
    private val block: () -> ByteReadChannel
) : RequestBody(), CoroutineScope {

    override val coroutineContext: CoroutineContext
        get() = callContext + producerJob + Dispatchers.IO

    private val producerJob = Job(callContext[Job])

    override fun contentType(): MediaType? = null

    override fun writeTo(sink: BufferedSink) {
        withJob(producerJob) {
            while (producerJob.isActive) {
                block().toInputStream().source().use {
                    sink.writeAll(it)
                }
            }
        }
    }

    override fun contentLength(): Long = contentLength ?: -1

    /**
     * Completes the given job when the block returns calling either `complete()` when the block runs
     * successfully or `completeExceptionally()` on exception.
     * @return the result of calling [block]
     */
    private inline fun <T> withJob(job: CompletableJob, block: () -> T): T {
        try {
            return block()
        } catch (ex: Exception) {
            job.completeExceptionally(ex)
            // wrap all exceptions thrown from inside `okhttp3.RequestBody#writeTo(..)` as an IOException
            // see https://github.com/awslabs/aws-sdk-kotlin/issues/733
            throw IOException(ex)
        } finally {
            job.complete()
        }
    }

}
