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

import com.github.ajalt.clikt.core.subcommands
import com.wire.kalium.cli.CLIApplication
import com.wire.kalium.cli.commands.AddMemberToGroupCommand
import com.wire.kalium.cli.commands.CreateGroupCommand
import com.wire.kalium.cli.commands.DeleteClientCommand
import com.wire.kalium.cli.commands.GenerateEventsCommand
import com.wire.kalium.cli.commands.ListenGroupCommand
import com.wire.kalium.cli.commands.LoginCommand
import com.wire.kalium.cli.commands.MarkAsReadCommand
import com.wire.kalium.cli.commands.RefillKeyPackagesCommand
import com.wire.kalium.cli.commands.RemoveMemberFromGroupCommand
import com.wire.kalium.cli.commands.InteractiveCommand
import com.wire.kalium.cli.commands.UpdateSupportedProtocolsCommand

fun main(args: Array<String>) = CLIApplication().subcommands(
    LoginCommand().subcommands(
        CreateGroupCommand(),
        ListenGroupCommand(),
        DeleteClientCommand(),
        AddMemberToGroupCommand(),
        RemoveMemberFromGroupCommand(),
        RefillKeyPackagesCommand(),
        MarkAsReadCommand(),
        InteractiveCommand(),
        UpdateSupportedProtocolsCommand(),
        GenerateEventsCommand()
    )
).main(args)
