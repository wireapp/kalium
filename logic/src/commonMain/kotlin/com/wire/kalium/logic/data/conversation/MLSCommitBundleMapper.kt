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
