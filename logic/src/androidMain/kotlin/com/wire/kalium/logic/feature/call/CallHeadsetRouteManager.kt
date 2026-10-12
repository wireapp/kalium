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

import android.annotation.TargetApi
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executor

/**
 * Keeps a call on its Bluetooth headset when wear detection changes the system route.
 * AVS reports communication-device changes without restoring the headset. Its device
 * connection callback also misses recovery when the headset stays connected throughout.
 * All state and routing requests run on the main thread, alongside Android's callbacks.
 */
@TargetApi(Build.VERSION_CODES.S)
internal class CallHeadsetRouteManager(
    private val audioManager: AudioManager,
    private val handler: Handler = Handler(Looper.getMainLooper()),
) {
    private var inCall = false
    private var speakerRequested = false
    private var preferredHeadset: AudioDeviceInfo? = null
    private var pendingDeviceId: Int? = null
    private var restoreAttempts = 0
    private val executor = Executor { handler.post(it) }
    private val retry = Runnable {
        pendingDeviceId = null
        updateRoute()
    }
    private val routeListener = AudioManager.OnCommunicationDeviceChangedListener { device ->
        // The selected device may change again before this callback runs. Confirm our request
        // from the event itself, then reconcile against the latest route.
        if (pendingDeviceId != null && device?.id == pendingDeviceId) cancelRequest()
        updateRoute()
    }
    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<AudioDeviceInfo>) = devicesChanged()
        override fun onAudioDevicesRemoved(removedDevices: Array<AudioDeviceInfo>) = devicesChanged()
    }

    fun onCallStateChanged(active: Boolean) {
        handler.post {
            if (active == inCall) return@post
            inCall = active
            if (active) {
                audioManager.addOnCommunicationDeviceChangedListener(executor, routeListener)
                audioManager.registerAudioDeviceCallback(deviceCallback, handler)
                updateRoute()
            } else {
                audioManager.removeOnCommunicationDeviceChangedListener(routeListener)
                audioManager.unregisterAudioDeviceCallback(deviceCallback)
                cancelRequest()
                preferredHeadset = null
                speakerRequested = false
            }
        }
    }

    fun onSpeakerRequested(enabled: Boolean) {
        handler.post {
            speakerRequested = enabled
            cancelRequest()
            updateRoute()
        }
    }

    private fun devicesChanged() {
        if (!inCall) return
        // A reconnect can provide a new AudioDeviceInfo and invalidate an outstanding request.
        cancelRequest()
        updateRoute()
    }

    private fun updateRoute() {
        if (!inCall || audioManager.mode != AudioManager.MODE_IN_COMMUNICATION) return

        val current = audioManager.communicationDevice
        val devices = audioManager.availableCommunicationDevices
        when {
            speakerRequested -> updateSpeakerRoute(current, devices)
            current?.isBluetoothHeadset() == true -> {
                preferredHeadset = current
                cancelRequest()
            }
            canRestoreHeadset(current, devices) -> restoreHeadset(devices)
        }
    }

    private fun updateSpeakerRoute(current: AudioDeviceInfo?, devices: List<AudioDeviceInfo>) {
        if (current?.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) {
            cancelRequest()
        } else {
            devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }?.let { requestRoute(it) }
        }
    }

    private fun canRestoreHeadset(current: AudioDeviceInfo?, devices: List<AudioDeviceInfo>): Boolean {
        // Let AVS handle a wired/USB headset or another explicitly selected external output.
        val usingBuiltInOutput = current == null || current.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE ||
            current.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
        val wiredHeadsetAvailable = devices.any {
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET || it.type == AudioDeviceInfo.TYPE_USB_HEADSET
        }
        return usingBuiltInOutput && !wiredHeadsetAvailable
    }

    private fun restoreHeadset(devices: List<AudioDeviceInfo>) {
        val headsets = devices.filter { it.isBluetoothHeadset() }
        val preferred = preferredHeadset
        val headset = if (preferred == null) {
            headsets.firstOrNull()
        } else {
            headsets.firstOrNull { it.id == preferred.id } ?: headsets.firstOrNull {
                preferred.address.isNotEmpty() && it.address == preferred.address
            } ?: headsets.singleOrNull()?.takeIf { preferred.address.isEmpty() }
        }
        if (headset == null) {
            // Remember the preference through a temporary disconnect, but never select a stale device.
            handler.removeCallbacks(retry)
            pendingDeviceId = null
        } else {
            preferredHeadset = headset
            requestRoute(headset)
        }
    }

    private fun requestRoute(device: AudioDeviceInfo) {
        if (pendingDeviceId == device.id || restoreAttempts >= MAX_RESTORE_ATTEMPTS) return
        pendingDeviceId = device.id
        restoreAttempts++
        val accepted = try {
            audioManager.setCommunicationDevice(device)
        } catch (_: IllegalArgumentException) {
            // The device can disappear between enumerating devices and selecting it.
            false
        }
        // Android applies an accepted request asynchronously. Wait before retrying, and bound retries.
        handler.removeCallbacks(retry)
        handler.postDelayed(retry, if (accepted) REQUEST_TIMEOUT_MS else REJECTED_REQUEST_RETRY_MS)
    }

    private fun cancelRequest() {
        handler.removeCallbacks(retry)
        pendingDeviceId = null
        restoreAttempts = 0
    }

    private fun AudioDeviceInfo.isBluetoothHeadset(): Boolean =
        type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || type == AudioDeviceInfo.TYPE_BLE_HEADSET

    private companion object {
        const val MAX_RESTORE_ATTEMPTS = 3
        const val REQUEST_TIMEOUT_MS = 30_000L
        const val REJECTED_REQUEST_RETRY_MS = 1_000L
    }
}
