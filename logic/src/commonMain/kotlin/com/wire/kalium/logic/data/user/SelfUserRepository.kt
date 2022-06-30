package com.wire.kalium.logic.data.user

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.wrapApiRequest
import com.wire.kalium.logic.wrapStorageRequest
import com.wire.kalium.network.api.user.self.SelfApi
import com.wire.kalium.persistence.dao.SelfUserDao


// Class responsible for manipulating SelfUser on repo level,
// This is the only entry point for the SelfUser,
// Since SelfUserDao manipulates the local SelfUser, if we decide to
// use another model on the SelfUserDao, we just need to adjust it here, perhaps
// change the logic of the mapper
class SelfUserRepository(
    private val selfUserDao: SelfUserDao,
    private val selfUserApi: SelfApi,
    private val userMapper: UserMapper
) {

    suspend fun updateSelfUser(newName: String?, newAccent: Int?, newAssetId: String?): Either<CoreFailure, SelfUser> {
        val user = selfUserDao.getSelfUserEntity()
        val updateRequest = userMapper.fromModelToUpdateApiModel(user, newName, newAccent, newAssetId)
        return wrapApiRequest { selfUserApi.updateSelf(updateRequest) }
            .map { userMapper.fromUpdateRequestToDaoModel(user, updateRequest) }
            .flatMap { userEntity ->
                wrapStorageRequest {
                    selfUserDao.updateSelfUser(userEntity)
                }.map { userMapper.fromDaoModelToSelfUser(userEntity) }
            }
    }

    ... remaining methods that are going to manipulate the SelfUser on repo level

}
