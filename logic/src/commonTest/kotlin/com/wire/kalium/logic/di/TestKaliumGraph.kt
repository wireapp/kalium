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

package com.wire.kalium.logic.di

import com.wire.kalium.logic.feature.client.LegacyClientDependencies
import com.wire.kalium.logic.feature.user.LegacyUserDependencies
import com.wire.kalium.logic.feature.search.LegacySearchDependencies
import com.wire.kalium.logic.feature.channels.LegacyChannelsDependencies
import com.wire.kalium.logic.feature.meeting.LegacyMeetingDependencies
import com.wire.kalium.logic.feature.debug.LegacyDebugDependencies
import com.wire.kalium.logic.feature.conversation.LegacyConversationDependencies
import com.wire.kalium.logic.feature.message.LegacyMessageDependencies
import dev.mokkery.mock

/** Consumer-only tests must not resolve any unrelated global resource input or owner. */
internal fun createKaliumGraph(): KaliumGraph = createKaliumGraph(
    mock<GlobalGraphInputs>(),
    GlobalGraphOwner { error("Consumer-only resolution requested the global runtime owner") },
)

/** Old consumer-only fixtures do not resolve the newly migrated facade families. */
internal fun KaliumUserGraph.Factory.create(
    dependencies: UserGraphDependencies,
    legacyConversationDependencies: LegacyConversationDependencies,
    legacyMessageDependencies: LegacyMessageDependencies,
): KaliumUserGraph = create(
    dependencies = dependencies,
    legacyConversationDependencies = legacyConversationDependencies,
    legacyMessageDependencies = legacyMessageDependencies,
    legacyClientDependencies = mock<LegacyClientDependencies>(),
    legacyUserDependencies = mock<LegacyUserDependencies>(),
    legacySearchDependencies = mock<LegacySearchDependencies>(),
    legacyChannelsDependencies = mock<LegacyChannelsDependencies>(),
    legacyMeetingDependencies = mock<LegacyMeetingDependencies>(),
    legacyDebugDependencies = mock<LegacyDebugDependencies>(),
    userSessionResourceInputs = mock<UserSessionResourceInputs>(),
    userSessionStateInputs = mock<UserSessionStateInputs>(),
    userSessionOwnershipInputs = mock<UserSessionOwnershipInputs>(),
    userSessionGlobalInputs = mock<UserSessionGlobalInputs>(),
)
