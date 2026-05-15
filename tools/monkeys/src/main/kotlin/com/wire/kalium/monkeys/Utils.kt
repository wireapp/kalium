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
package com.wire.kalium.monkeys

import com.wire.kalium.monkeys.model.MonkeyId
import com.wire.kalium.monkeys.model.UserData
import java.util.Optional
import java.util.concurrent.TimeUnit

@Suppress("SpreadOperator")
fun String.runSysCommand(wait: Optional<Long>) {
    val proc = ProcessBuilder(*split(" ").toTypedArray()).redirectOutput(ProcessBuilder.Redirect.INHERIT)
        .redirectError(ProcessBuilder.Redirect.INHERIT).start()
    if (wait.isPresent) {
        proc.waitFor(wait.get(), TimeUnit.SECONDS)
    }
}

fun String.renderMonkeyTemplate(userData: UserData, monkeyId: MonkeyId): String {
    return this.replace("{{teamName}}", userData.team.name).replace("{{email}}", userData.email)
        .replace("{{userId}}", userData.userId.value).replace("{{teamId}}", userData.team.id)
        .replace("{{monkeyIndex}}", monkeyId.index.toString()).replace("{{monkeyClientId}}", monkeyId.clientId.toString())
        .replace("{{code}}", userData.oldCode.orEmpty())
}
