/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.AudioDeviceInfoBuilder
import org.robolectric.util.ReflectionHelpers
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.S, Build.VERSION_CODES.BAKLAVA])
class CallHeadsetRouteManagerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val shadowAudioManager = shadowOf(audioManager)
    private val manager = CallHeadsetRouteManager(audioManager)
    private val earpiece = device(AudioDeviceInfo.TYPE_BUILTIN_EARPIECE, 1)
    private val speaker = device(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, 2)
    private val headset = device(AudioDeviceInfo.TYPE_BLUETOOTH_SCO, 3, "headset")

    @Before
    fun setup() {
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        shadowAudioManager.setAvailableCommunicationDevices(listOf(earpiece, speaker, headset))
        audioManager.setCommunicationDevice(headset)
    }

    @Test
    fun givenHeadsetStillConnected_whenRouteFallsBackToEarpiece_thenRestoreHeadset() {
        startCall()
        changeRoute(earpiece)
        assertRoute(headset)
    }

    @Test
    fun givenHeadsetStillConnected_whenRouteBecomesNull_thenRestoreHeadset() {
        startCall()
        audioManager.clearCommunicationDevice()
        shadowAudioManager.callOnCommunicationDeviceChangedListeners(null)
        idle()
        assertRoute(headset)
    }

    @Test
    fun givenHeadsetDisconnects_whenSameHeadsetReturnsWithNewId_thenRestoreHeadset() {
        startCall()
        shadowAudioManager.removeAvailableCommunicationDevice(headset, true)
        changeRoute(earpiece)
        assertRoute(earpiece)

        val reconnected = device(AudioDeviceInfo.TYPE_BLUETOOTH_SCO, 4, "headset")
        shadowAudioManager.addAvailableCommunicationDevice(reconnected, true)
        idle()
        assertRoute(reconnected)
    }

    @Test
    fun givenPreferredHeadsetMissing_whenDifferentHeadsetAppears_thenKeepFallback() {
        startCall()
        shadowAudioManager.removeAvailableCommunicationDevice(headset, true)
        changeRoute(earpiece)
        shadowAudioManager.addAvailableCommunicationDevice(device(AudioDeviceInfo.TYPE_BLUETOOTH_SCO, 4, "other"), true)
        idle()
        assertRoute(earpiece)
    }

    @Test
    fun givenSpeakerExplicitlyEnabled_whenHeadsetReconnects_thenKeepSpeaker() {
        startCall()
        manager.onSpeakerRequested(true)
        idle()
        changeRoute(speaker)
        shadowAudioManager.removeAvailableCommunicationDevice(headset, true)
        shadowAudioManager.addAvailableCommunicationDevice(headset, true)
        idle()
        assertRoute(speaker)
    }

    @Test
    fun givenSpeakerExplicitlyEnabled_whenSpeakerDisabled_thenRestoreHeadset() {
        startCall()
        manager.onSpeakerRequested(true)
        idle()
        changeRoute(speaker)
        manager.onSpeakerRequested(false)
        idle()
        assertRoute(headset)
    }

    @Test
    fun givenSpeakerExplicitlyEnabled_whenAvsSelectsReconnectedHeadset_thenRestoreSpeaker() {
        startCall()
        manager.onSpeakerRequested(true)
        idle()
        // Queue the speaker confirmation, then change the route before that event is delivered.
        shadowAudioManager.callOnCommunicationDeviceChangedListeners(speaker)
        changeRoute(headset)
        assertRoute(speaker)
    }

    @Test
    fun givenWiredHeadsetAvailable_whenRouteFallsBack_thenDoNotOverrideWiredRouting() {
        startCall()
        shadowAudioManager.addAvailableCommunicationDevice(device(AudioDeviceInfo.TYPE_WIRED_HEADSET, 4), true)
        changeRoute(earpiece)
        assertRoute(earpiece)
    }

    @Test
    fun givenBleHeadset_whenRouteFallsBack_thenRestoreBleHeadset() {
        val bleHeadset = device(AudioDeviceInfo.TYPE_BLE_HEADSET, 4, "ble")
        shadowAudioManager.setAvailableCommunicationDevices(listOf(earpiece, speaker, bleHeadset))
        audioManager.setCommunicationDevice(bleHeadset)
        startCall()
        changeRoute(earpiece)
        assertRoute(bleHeadset)
    }

    @Test
    fun givenOnlyA2dpDevice_whenCallStarts_thenDoNotSelectMusicOutputForCall() {
        shadowAudioManager.setAvailableCommunicationDevices(
            listOf(earpiece, speaker, device(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, 4))
        )
        audioManager.setCommunicationDevice(earpiece)
        startCall()
        assertRoute(earpiece)
    }

    @Test
    fun givenCallEnded_whenRouteChanges_thenDoNotRestoreHeadset() {
        startCall()
        manager.onCallStateChanged(false)
        idle()
        changeRoute(earpiece)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(2))
        assertRoute(earpiece)
    }

    @Test
    fun givenCommunicationModeEndedBeforeCallCallback_whenRouteChanges_thenDoNotRestoreHeadset() {
        startCall()
        audioManager.mode = AudioManager.MODE_NORMAL
        changeRoute(earpiece)
        assertRoute(earpiece)
    }

    @Test
    fun givenRouteRequestRejected_whenRetriesExhausted_thenStopRetrying() {
        startCall()
        audioManager.setCommunicationDevice(earpiece)
        shadowAudioManager.lockCommunicationDevice(true)
        shadowAudioManager.callOnCommunicationDeviceChangedListeners(earpiece)
        idle()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(5))
        shadowAudioManager.lockCommunicationDevice(false)
        shadowAudioManager.callOnCommunicationDeviceChangedListeners(null)
        idle()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(2))
        assertRoute(earpiece)
    }

    @Test
    fun givenCallEndedDuringRejectedRequest_whenRetryWouldRun_thenDoNotChangeOutput() {
        startCall()
        audioManager.setCommunicationDevice(earpiece)
        shadowAudioManager.lockCommunicationDevice(true)
        shadowAudioManager.callOnCommunicationDeviceChangedListeners(earpiece)
        idle()
        manager.onCallStateChanged(false)
        idle()
        shadowAudioManager.lockCommunicationDevice(false)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(2))
        assertRoute(earpiece)
    }

    private fun startCall() {
        manager.onCallStateChanged(true)
        idle()
    }

    private fun changeRoute(device: AudioDeviceInfo) {
        audioManager.setCommunicationDevice(device)
        shadowAudioManager.callOnCommunicationDeviceChangedListeners(device)
        idle()
    }

    private fun assertRoute(device: AudioDeviceInfo) = assertEquals(device.id, audioManager.communicationDevice?.id)

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun device(type: Int, id: Int, address: String = ""): AudioDeviceInfo =
        AudioDeviceInfoBuilder.newBuilder().setType(type).build().also {
            val port = ReflectionHelpers.getField<Any>(it, "mPort")
            ReflectionHelpers.setField(port, "mAddress", address)
            ReflectionHelpers.setField(ReflectionHelpers.getField<Any>(port, "mHandle"), "mId", id)
        }
}
