/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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

package com.wire.kalium.logic.data.conversation

import com.wire.kalium.cryptography.CommitBundle
import com.wire.kalium.network.api.base.authenticated.message.MLSMessageApi
import com.wire.kalium.protobuf.mls.GroupInfoBundle
import com.wire.kalium.protobuf.mls.GroupInfoType
import com.wire.kalium.protobuf.mls.RatchetTreeType
import pbandk.ByteArr
import pbandk.encodeToByteArray

interface MLSCommitBundleMapper {
    fun toProtobuf(bundle: CommitBundle): com.wire.kalium.protobuf.mls.CommitBundle

    fun toDTO(bundle: CommitBundle): MLSMessageApi.CommitBundle
}

class MLSCommitBundleMapperImpl : MLSCommitBundleMapper {
    override fun toProtobuf(bundle: CommitBundle) =
        com.wire.kalium.protobuf.mls.CommitBundle(
            ByteArr(bundle.commit),
            bundle.welcome?.let {
                ByteArr(it)
            },
            groupInfoBundle = GroupInfoBundle(
                groupInfo = ByteArr(bundle.publicGroupStateBundle.payload),
                groupInfoType = GroupInfoType.PUBLIC_GROUP_STATE, // TODO: later map it from the bundle object
                ratchetTreeType = RatchetTreeType.fromName(bundle.publicGroupStateBundle.ratchetTreeType.name)
            )
        )

    override fun toDTO(bundle: CommitBundle) = MLSMessageApi.CommitBundle(toProtobuf(bundle).encodeToByteArray())

}
