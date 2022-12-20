package com.wire.kalium.network

import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.coroutines.CancellationException
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

/**
 * A [RequestBody] implementation that reads data from a [ByteReadChannel] and writes it to a [BufferedSink].
 *
 * @property contentLength the length of the data to be written, or `null` if the length is unknown
 * @property callContext the [CoroutineContext] to be used for suspending operations
 * @property block a function that returns a [ByteReadChannel] to be written to the [BufferedSink]
 *
 * @constructor Creates a new instance of [ByteChannelRequestBody].
 */
class ByteChannelRequestBody(
    private val contentLength: Long?,
    private val callContext: CoroutineContext,
    private val block: () -> ByteReadChannel
) : RequestBody(), CoroutineScope {

    /**
     * The [CoroutineContext] for this [CoroutineScope].
     *
     * It is composed of the [callContext] and a new [Job] and [Dispatchers.IO] to ensure that the
     * block provided in the constructor is run on the IO dispatcher.
     */
    override val coroutineContext: CoroutineContext
        get() = callContext + producerJob + Dispatchers.IO

    /**
     * The [Job] used to manage the lifecycle of the block provided in the constructor.
     * This will be the [Job] coming from the [CoroutineContext] provided in the constructor.
     * Which will be the Job created within the Ktor
     */
    private val producerJob = Job(callContext[Job])

    override fun contentType(): MediaType? = null

    /**
     * Writes the data from the [ByteReadChannel] provided by the block in the constructor to the [BufferedSink].
     *
     * If the [producerJob] is still active, the [ByteReadChannel] is converted to an [InputStream] and its
     * data is written to the [BufferedSink] using [BufferedSink.writeAll]. If the [producerJob] is not
     * active, this method does nothing.
     *
     * If an exception is thrown while writing to the [BufferedSink], the [producerJob] is completed
     * exceptionally with the exception and the exception is wrapped as an [IOException] and rethrown.
     */
    override fun writeTo(sink: BufferedSink) {
        withJob(producerJob) {
            if (producerJob.isActive) {
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
            println("Exception in withJob: $ex")
            job.completeExceptionally(ex)
            // wrap all exceptions thrown from inside `okhttp3.RequestBody#writeTo(..)` as an IOException
            // see https://github.com/awslabs/aws-sdk-kotlin/issues/733
            throw IOException(ex)
        } finally {
            job.complete()
        }
    }

}
