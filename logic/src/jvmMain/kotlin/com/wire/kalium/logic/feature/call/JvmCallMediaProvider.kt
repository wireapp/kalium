/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
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

package com.wire.kalium.logic.feature.call

import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.util.PlatformRotation
import kotlinx.coroutines.flow.StateFlow

/**
 * Media operations owned by the existing JVM calling runtime.
 *
 * The implementation owns and revalidates the active account/conversation generation, permission-bound
 * admission and native acknowledgements for every operation. Registering a provider does not initialize
 * a runtime, acquire a device or admit a call. Unsupported controls must throw [UnsupportedOperationException].
 */
public interface JvmCallMedia {
    public val readiness: StateFlow<JvmMediaReadiness>
    public val route: StateFlow<JvmAudioRouteState>

    /** Validates the inert runtime. Hardware admission happens per call, after its native handle exists. */
    public suspend fun startMediaManager()

    /** Validates the inert runtime/router; never constructs another AVSFlowManager or starts devices. */
    public suspend fun startFlowManager()

    public suspend fun setSpeaker(enabled: Boolean)

    /**
     * [surfaceToken] is an opaque positive registry token, never a native pointer. Zero detaches the
     * preview of the matching active conversation lease. Negative tokens are invalid.
     */
    public suspend fun setVideoPreview(conversationId: ConversationId, surfaceToken: Int)

    public suspend fun setUIRotation(rotation: PlatformRotation)
    public suspend fun flipCamera(conversationId: ConversationId, front: Boolean)
}

/** Permission/admission preparation and actual device readiness are distinct states. */
public sealed interface JvmMediaReadiness {
    public data object Unprepared : JvmMediaReadiness

    /** Permission/admission preparation only; this does NOT mean audio devices are running or ready. */
    public data object Prepared : JvmMediaReadiness

    /** Actual audio-device readiness acknowledged under the current permission-bound admission. */
    public data object Ready : JvmMediaReadiness

    /** Admission is fenced; native stop/drain has not acknowledged completion. */
    public data object Stopping : JvmMediaReadiness

    /** Devices and frame delivery are stopped; the generation still awaits final call release. */
    public data object Quiescent : JvmMediaReadiness
}

/** Observed hardware route; requested or dummy AVS routing must never publish [Confirmed]. */
public sealed interface JvmAudioRouteState {
    public data object Unknown : JvmAudioRouteState
    public data object Unsupported : JvmAudioRouteState
    public data class Confirmed(public val deviceUid: String, public val speaker: Boolean) : JvmAudioRouteState
}

/** Scoped registry for injection and tests. A registered provider is retained for this registry's lifetime. */
public class JvmCallMediaRegistry {
    private val lock = Any()
    private var media: JvmCallMedia? = null

    public fun register(media: JvmCallMedia): Unit = synchronized(lock) {
        check(this.media == null || this.media === media) {
            "JVM call media is already registered; reuse the existing process runtime provider."
        }
        this.media = media
    }

    public fun requireMedia(): JvmCallMedia = synchronized(lock) {
        checkNotNull(media) {
            "JVM call media is not registered. Register the process runtime's JvmCallMedia with " +
                "JvmCallMediaProvider.register before constructing calling services."
        }
    }
}

/** Process-scoped registry. Same-instance registration is idempotent; replacement and unregister are unsupported. */
public object JvmCallMediaProvider {
    internal val registry: JvmCallMediaRegistry = JvmCallMediaRegistry()

    public fun register(media: JvmCallMedia): Unit = registry.register(media)
}
