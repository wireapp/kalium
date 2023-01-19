package com.wire.kalium.logic.feature.asset

import com.wire.kalium.logic.feature.user.IsSelfATeamMemberUseCase

fun interface GetAssetSizeLimitUseCase {
    /**
     * Returns the maximum size of an asset that can be uploaded to the Wire backend.
     * @param isImage whether the asset to upload is an image or not
     */
    suspend operator fun invoke(isImage: Boolean): Long
}

internal class GetAssetSizeLimitUseCaseImpl internal constructor(
    private val isSelfATeamMember: IsSelfATeamMemberUseCase
) : GetAssetSizeLimitUseCase {
    override suspend operator fun invoke(isImage: Boolean): Long {
        val hasUserTeam = isSelfATeamMember()
        return when {
            isImage -> IMAGE_SIZE_LIMIT_BYTES
            hasUserTeam -> ASSET_SIZE_TEAM_USER_LIMIT_BYTES
            else -> ASSET_SIZE_DEFAULT_LIMIT_BYTES
        }
    }

    companion object {
        const val IMAGE_SIZE_LIMIT_BYTES = 15L * 1024 * 1024 // 15 MB limit for images
        const val ASSET_SIZE_DEFAULT_LIMIT_BYTES = 25L * 1024 * 1024 // 25 MB asset default user limit size
        const val ASSET_SIZE_TEAM_USER_LIMIT_BYTES = 100L * 1024 * 1024 // 100 MB asset team user limit size
    }
}
