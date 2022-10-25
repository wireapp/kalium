package com.wire.kalium.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.PrintMessage
import com.github.ajalt.clikt.core.requireObject
import com.wire.kalium.logic.feature.UserSessionScope
import com.wire.kalium.logic.feature.keypackage.RefillKeyPackagesResult
import kotlinx.coroutines.runBlocking

class RefillKeyPackagesCommand : CliktCommand(name = "refill-key-packages") {

    private val userSession by requireObject<UserSessionScope>()

    override fun run() = runBlocking {
        when (val result = userSession.client.refillKeyPackages()) {
            is RefillKeyPackagesResult.Success -> echo("key packages were refilled")
            is RefillKeyPackagesResult.Failure -> throw PrintMessage("refill key packages failed: ${result.failure}")
        }
    }
}
