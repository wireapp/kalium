@file:Suppress("TooManyFunctions")
package com.wire.kalium.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.enum
import com.github.ajalt.clikt.parameters.types.file
import com.wire.kalium.cli.commands.AddMemberToGroupCommand
import com.wire.kalium.cli.commands.ConsoleCommand
import com.wire.kalium.cli.commands.CreateGroupCommand
import com.wire.kalium.cli.commands.DeleteClientCommand
import com.wire.kalium.cli.commands.ListenGroupCommand
import com.wire.kalium.cli.commands.LoginCommand
import com.wire.kalium.cli.commands.RefillKeyPackagesCommand
import com.wire.kalium.cli.commands.RemoveMemberFromGroupCommand
import com.wire.kalium.logger.FileLogger
import com.wire.kalium.logger.KaliumLogLevel
import com.wire.kalium.logic.CoreLogger
import com.wire.kalium.logic.CoreLogic
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import kotlinx.coroutines.runBlocking
import java.io.File

class CLIApplication : CliktCommand(allowMultipleSubcommands = true) {

    private val logLevel by option(help = "log level").enum<KaliumLogLevel>().default(KaliumLogLevel.WARN)
    private val logOutputFile by option(help = "output file for logs").file(canBeDir = false)
    private val developmentApiEnabled by option(help = "use development API if supported by backend").flag(default = false)
    private val fileLogger: FileLogger by lazy { FileLogger(logOutputFile ?: File("kalium.log")) }

    override fun run() = runBlocking {
        currentContext.findOrSetObject {
            CoreLogic(
                clientLabel = "Kalium CLI",
                rootPath = "$HOME_DIRECTORY/.kalium/accounts",
                kaliumConfigs = KaliumConfigs(developmentApiEnabled = developmentApiEnabled)
            )
        }

        if (logOutputFile != null) {
            CoreLogger.setLoggingLevel(logLevel, fileLogger)
        } else {
            CoreLogger.setLoggingLevel(logLevel)
        }
    }

    companion object {
        val HOME_DIRECTORY: String = System.getProperty("user.home")
    }

}

fun main(args: Array<String>) = CLIApplication().subcommands(
    LoginCommand().subcommands(
        CreateGroupCommand(),
        ListenGroupCommand(),
        DeleteClientCommand(),
        AddMemberToGroupCommand(),
        RemoveMemberFromGroupCommand(),
        ConsoleCommand(),
        RefillKeyPackagesCommand()
    )
).main(args)
