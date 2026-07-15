@file:OptIn(
    com.wire.kalium.calling.runtime.ExperimentalCallingRuntimeApi::class,
    com.wire.kalium.logic.service.api.ExperimentalKaliumServiceApi::class,
)
@file:Suppress("TooGenericExceptionCaught")

/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.wire.kalium.sample.callrecorder

import com.wire.kalium.calling.runtime.CallingFailure
import com.wire.kalium.calling.runtime.CallingResult
import com.wire.kalium.logic.service.api.ServiceCalling
import java.io.BufferedOutputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.SecureRandom
import java.time.Clock
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.HexFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Small boundary around the real AVS playout-recording facility. */
internal interface AudioRecorder : AutoCloseable {
    suspend fun start(): AudioRecordingResult

    suspend fun finish(): AudioRecordingResult

    override fun close()
}

internal sealed interface AudioRecordingResult {
    data class Started(val output: Path) : AudioRecordingResult

    data class Finished(val output: Path) : AudioRecordingResult

    data object NoRecording : AudioRecordingResult

    data class Failure(val description: String, val cause: Throwable? = null) : AudioRecordingResult
}

/**
 * Selects AVS' process-global, raw 16 kHz mono signed-16 playout recorder and converts its output
 * into a WAV file after AVS has stopped playout. One instance owns at most one sequential call.
 */
internal class AvsWavAudioRecorder(
    private val calls: ServiceCalling,
    outputDirectory: Path,
    private val clock: Clock = Clock.systemUTC(),
    private val random: SecureRandom = SecureRandom(),
) : AudioRecorder {
    private val directory = outputDirectory.toAbsolutePath().normalize()
    private var recording: RecordingPaths? = null
    private var closed = false

    override suspend fun start(): AudioRecordingResult = withContext(Dispatchers.IO) {
        if (closed) return@withContext AudioRecordingResult.Failure("Audio recorder is closed")
        if (recording != null) return@withContext AudioRecordingResult.Failure("An audio recording is already active")
        try {
            Files.createDirectories(directory)
            check(Files.isDirectory(directory) && Files.isWritable(directory)) {
                "Recording directory is not writable"
            }
            val paths = allocatePaths()
            Files.createFile(paths.rawPartial)
            when (val result = calls.recordAudio(paths.rawPartial.toString())) {
                CallingResult.Success -> {
                    recording = paths
                    AudioRecordingResult.Started(paths.wav)
                }
                is CallingResult.Failure -> {
                    Files.deleteIfExists(paths.rawPartial)
                    AudioRecordingResult.Failure(result.failure.safeDescription())
                }
            }
        } catch (failure: Throwable) {
            AudioRecordingResult.Failure("Unable to prepare AVS audio recording", failure)
        }
    }

    override suspend fun finish(): AudioRecordingResult = withContext(Dispatchers.IO) {
        val paths = recording ?: return@withContext AudioRecordingResult.NoRecording
        recording = null
        try {
            awaitRawFileClosed(paths.rawPartial)
            writeWav(paths.rawPartial, paths.wavPartial)
            moveAtomically(paths.wavPartial, paths.wav)
            Files.delete(paths.rawPartial)
            AudioRecordingResult.Finished(paths.wav)
        } catch (failure: Throwable) {
            runCatching { Files.deleteIfExists(paths.wavPartial) }
            AudioRecordingResult.Failure(
                "Unable to finalize WAV; raw PCM remains at ${paths.rawPartial.fileName}",
                failure,
            )
        }
    }

    override fun close() {
        closed = true
    }

    private fun allocatePaths(): RecordingPaths {
        val timestamp = FILE_TIME_FORMAT.format(clock.instant())
        repeat(MAX_ALLOCATION_ATTEMPTS) {
            val opaqueId = ByteArray(OPAQUE_ID_BYTES).also(random::nextBytes).let(HexFormat.of()::formatHex)
            val base = "call_${timestamp}_$opaqueId"
            val paths = RecordingPaths(
                rawPartial = directory.resolve("$base.pcm.partial"),
                wavPartial = directory.resolve("$base.wav.partial"),
                wav = directory.resolve("$base.wav"),
            )
            if (paths.all.none(Files::exists)) return paths
        }
        error("Unable to allocate a unique recording filename")
    }

    private fun writeWav(raw: Path, partialWav: Path) {
        val dataSize = Files.size(raw)
        require(dataSize <= MAX_WAV_DATA_SIZE) { "Recording is too large for a RIFF/WAV file" }
        DataOutputStream(
            BufferedOutputStream(
                Files.newOutputStream(partialWav, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE),
            ),
        ).use { output ->
            output.writeAscii("RIFF")
            output.writeLittleEndianInt((WAV_HEADER_AFTER_RIFF + dataSize).toInt())
            output.writeAscii("WAVE")
            output.writeAscii("fmt ")
            output.writeLittleEndianInt(PCM_FORMAT_SIZE)
            output.writeLittleEndianShort(PCM_FORMAT)
            output.writeLittleEndianShort(CHANNELS)
            output.writeLittleEndianInt(SAMPLE_RATE)
            output.writeLittleEndianInt(SAMPLE_RATE * CHANNELS * BYTES_PER_SAMPLE)
            output.writeLittleEndianShort(CHANNELS * BYTES_PER_SAMPLE)
            output.writeLittleEndianShort(BITS_PER_SAMPLE)
            output.writeAscii("data")
            output.writeLittleEndianInt(dataSize.toInt())
            Files.newInputStream(raw).use { input -> input.copyTo(output) }
        }
    }

    /** AVS emits its close callback just before media teardown, so wait for PCM writes to stop. */
    private fun awaitRawFileClosed(raw: Path) {
        val deadline = System.nanoTime() + RAW_CLOSE_TIMEOUT_NANOS
        var stableSince = System.nanoTime()
        var previousSize = Files.size(raw)
        while (System.nanoTime() < deadline) {
            Thread.sleep(RAW_SIZE_POLL_MILLIS)
            val size = Files.size(raw)
            if (size != previousSize) {
                previousSize = size
                stableSince = System.nanoTime()
            } else if (System.nanoTime() - stableSince >= RAW_STABLE_WINDOW_NANOS) {
                return
            }
        }
        error("AVS PCM output did not become stable before the finalization timeout")
    }

    private fun moveAtomically(source: Path, target: Path) {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE)
        } catch (unsupported: AtomicMoveNotSupportedException) {
            Files.move(source, target)
        }
    }

    private data class RecordingPaths(val rawPartial: Path, val wavPartial: Path, val wav: Path) {
        val all: List<Path> = listOf(rawPartial, wavPartial, wav)
    }

    private companion object {
        const val SAMPLE_RATE = 16_000
        const val CHANNELS = 1
        const val BITS_PER_SAMPLE = 16
        const val BYTES_PER_SAMPLE = BITS_PER_SAMPLE / 8
        const val PCM_FORMAT = 1
        const val PCM_FORMAT_SIZE = 16
        const val WAV_HEADER_AFTER_RIFF = 36L
        val MAX_WAV_DATA_SIZE: Long = UInt.MAX_VALUE.toLong() - WAV_HEADER_AFTER_RIFF
        const val OPAQUE_ID_BYTES = 12
        const val MAX_ALLOCATION_ATTEMPTS = 32
        const val RAW_SIZE_POLL_MILLIS = 50L
        const val RAW_STABLE_WINDOW_NANOS = 300_000_000L
        const val RAW_CLOSE_TIMEOUT_NANOS = 5_000_000_000L
        val FILE_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter
            .ofPattern("yyyyMMdd'T'HHmmssSSS'Z'")
            .withZone(ZoneOffset.UTC)
    }
}

private fun DataOutputStream.writeAscii(value: String) {
    write(value.toByteArray(Charsets.US_ASCII))
}

private fun DataOutputStream.writeLittleEndianInt(value: Int) {
    write(ByteBuffer.allocate(Int.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array())
}

private fun DataOutputStream.writeLittleEndianShort(value: Int) {
    write(ByteBuffer.allocate(Short.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN).putShort(value.toShort()).array())
}

private fun CallingFailure.safeDescription(): String = when (this) {
    is CallingFailure.Avs -> description
    CallingFailure.AlreadyActive -> "A call is already active"
    CallingFailure.ConcurrencyLimitReached -> "The call concurrency limit was reached"
    is CallingFailure.Conversation -> description
    is CallingFailure.Crypto -> description
    CallingFailure.NotActive -> "The call is not active"
    CallingFailure.RuntimeClosed -> "The calling runtime is closed"
    CallingFailure.RuntimeNotReady -> "The calling runtime is not ready"
    is CallingFailure.State -> description
    is CallingFailure.Transport -> description
}
