package com.wire.kalium.cli

import com.github.ajalt.clikt.core.subcommands
import com.wire.kalium.cli.commands.AddMemberToGroupCommand
import com.wire.kalium.cli.commands.CreateGroupCommand
import com.wire.kalium.cli.commands.DeleteClientCommand
import com.wire.kalium.cli.commands.ListenGroupCommand
import com.wire.kalium.cli.commands.LoginCommand
import com.wire.kalium.cli.commands.MarkAsReadCommand
import com.wire.kalium.cli.commands.RefillKeyPackagesCommand
import com.wire.kalium.cli.commands.RemoveMemberFromGroupCommand

fun main(args: Array<String>) = CLIApplication().subcommands(
    LoginCommand().subcommands(
        CreateGroupCommand(),
        ListenGroupCommand(),
        DeleteClientCommand(),
        AddMemberToGroupCommand(),
        RemoveMemberFromGroupCommand(),
        RefillKeyPackagesCommand(),
        MarkAsReadCommand()
    )
).main(args)
