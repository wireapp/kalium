/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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
package com.wire.kalium.cli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.PrintMessage
import com.github.ajalt.clikt.core.requireObject
import com.wire.kalium.cli.selectConversation
import com.wire.kalium.logic.feature.UserSessionScope
import com.wire.kalium.logic.feature.debug.BrokenState
import com.wire.kalium.logic.feature.debug.SendBrokenAssetMessageResult
import kotlinx.coroutines.runBlocking

class SendBrokenAssetCommand: CliktCommand(name = "send-broken-asset") {
    private val userSession by requireObject<UserSessionScope>()

    override fun run(): Unit = runBlocking {

        val selectedConversation = userSession.selectConversation()
        print("Enter asset data path: ")
        val assetDataPath = readln().takeIf { it.isNotBlank() } ?: error("Asset data path cannot be empty")
        print("Enter asset data size: ")
        val assetDataSize = readln().toLongOrNull() ?: error("Invalid asset data size")
        print("Enter asset name: ")
        val assetName = readln().takeIf { it.isNotBlank() } ?: error("Asset name cannot be empty")
        print("Enter asset MIME type: ")
        val assetMimeType = readln().takeIf { it.isNotBlank() } ?: error("Asset MIME type cannot be empty")
        print("Enter broken state (true/false): ")

        val result = userSession.debug.sendBrokenAssetMessage(
            conversationId = selectedConversation.id,
            assetDataPath = assetDataPath,
            assetDataSize = assetDataSize,
            assetName = assetName,
            assetMimeType = assetMimeType,
            brokenState = BrokenState(true, true, true),
        )

        when(result) {
            is SendBrokenAssetMessageResult.Failure -> throw PrintMessage("send-broken-asset failed: $result")
            SendBrokenAssetMessageResult.Success -> echo("Successfully sent broken asset message")
        }

    }
}
