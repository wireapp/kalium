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
package com.wire.kalium.logic.feature.team

import com.wire.kalium.logic.data.id.SelfTeamIdProvider
import com.wire.kalium.logic.data.sync.SlowSyncRepository
import com.wire.kalium.logic.data.team.TeamRepository
import com.wire.kalium.logic.di.KaliumTeamApi
import com.wire.kalium.logic.feature.LegacyFacadeLifetime
import com.wire.kalium.logic.feature.user.IsSelfATeamMemberUseCase
import com.wire.kalium.logic.feature.user.IsSelfATeamMemberUseCaseImpl
import dev.zacsweers.metro.GraphExtension
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.Qualifier
import dev.zacsweers.metro.SingleIn

/** Distinguishes this facade's captures from same-typed bindings in the parent and sibling facades. */
@Qualifier
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.VALUE_PARAMETER)
internal annotation class LegacyTeamBinding(val value: String)

/**
 * Factory ports are read independently by the facade instance, never captured in UserLifetime.
 * Session mapping: teamRepository and slowSyncRepository -> the matching session getters; selfTeamIdProvider -> selfTeamId.
 */
internal interface LegacyTeamDependencies {
    val teamRepository: TeamRepository
    val slowSyncRepository: SlowSyncRepository
    val selfTeamIdProvider: SelfTeamIdProvider
}

/** Individual entry points; capture accessors also preserve explicit legacy aliases. */
internal interface LegacyTeamApi : KaliumTeamApi {
    @get:LegacyTeamBinding("teamRepository")
    val teamTeamRepository: TeamRepository

    @get:LegacyTeamBinding("slowSyncRepository")
    val teamSlowSyncRepository: SlowSyncRepository

    @get:LegacyTeamBinding("selfTeamIdProvider")
    val teamSelfTeamIdProvider: SelfTeamIdProvider

    @get:LegacyTeamBinding("syncSelfTeamInfoUseCase")
    override val teamSyncSelfTeamInfoUseCase: SyncSelfTeamInfoUseCase

    @get:LegacyTeamBinding("isSelfATeamMember")
    override val teamIsSelfATeamMember: IsSelfATeamMemberUseCase
}

/** Per-instance capture providers and individual, unscoped use-case constructors. */
internal interface LegacyTeamBindings : LegacyTeamApi {
    @Provides
    @SingleIn(LegacyFacadeLifetime::class)
    @LegacyTeamBinding("teamRepository")
    fun provideTeamTeamRepository(dependencies: LegacyTeamDependencies): TeamRepository =
        dependencies.teamRepository

    @Provides
    @SingleIn(LegacyFacadeLifetime::class)
    @LegacyTeamBinding("slowSyncRepository")
    fun provideTeamSlowSyncRepository(dependencies: LegacyTeamDependencies): SlowSyncRepository =
        dependencies.slowSyncRepository

    @Provides
    @SingleIn(LegacyFacadeLifetime::class)
    @LegacyTeamBinding("selfTeamIdProvider")
    fun provideTeamSelfTeamIdProvider(dependencies: LegacyTeamDependencies): SelfTeamIdProvider =
        dependencies.selfTeamIdProvider

    @Provides
    @LegacyTeamBinding("syncSelfTeamInfoUseCase")
    fun provideTeamSyncSelfTeamInfoUseCase(
        @LegacyTeamBinding("selfTeamIdProvider") selfTeamIdProvider: SelfTeamIdProvider,
        @LegacyTeamBinding("teamRepository") teamRepository: TeamRepository,
    ): SyncSelfTeamInfoUseCase = SyncSelfTeamInfoUseCase(
        selfTeamIdProvider,
        teamRepository,
    )

    @Provides
    @LegacyTeamBinding("isSelfATeamMember")
    fun provideTeamIsSelfATeamMember(
        @LegacyTeamBinding("selfTeamIdProvider") selfTeamIdProvider: SelfTeamIdProvider,
        @LegacyTeamBinding("slowSyncRepository") slowSyncRepository: SlowSyncRepository,
    ): IsSelfATeamMemberUseCase = IsSelfATeamMemberUseCaseImpl(
        selfTeamIdProvider,
        slowSyncRepository,
    )
}

/**
 * One extension per TeamScope instance, generated at the final platform root.
 * Expose only this factory from the user graph; create a new extension on every facade getter.
 */
@GraphExtension(LegacyFacadeLifetime::class)
internal interface LegacyTeamFacadeGraph : LegacyTeamBindings {
    @GraphExtension.Factory
    interface Factory {
        fun create(@Provides dependencies: LegacyTeamDependencies): LegacyTeamFacadeGraph
    }
}
