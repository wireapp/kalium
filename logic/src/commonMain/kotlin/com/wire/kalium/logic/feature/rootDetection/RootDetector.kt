package com.wire.kalium.logic.feature.rootDetection

interface RootDetector {
    fun isSystemRooted(): Boolean
}

expect class RootDetectorImpl() : RootDetector
