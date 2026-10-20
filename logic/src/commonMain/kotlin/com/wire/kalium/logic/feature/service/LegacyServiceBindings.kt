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
package com.wire.kalium.logic.feature.service

import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.id.SelfTeamIdProvider
import com.wire.kalium.logic.data.service.ServiceRepository
import com.wire.kalium.logic.data.team.TeamRepository
import com.wire.kalium.logic.feature.LegacyFacadeLifetime
import com.wire.kalium.logic.feature.featureConfig.ObserveIsAppsAllowedForUsageUseCase
import com.wire.kalium.logic.feature.featureConfig.ObserveIsAppsAllowedForUsageUseCaseImpl
import dev.zacsweers.metro.GraphExtension
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.Qualifier
import dev.zacsweers.metro.SingleIn

/** Distinguishes this facade's captures from same-typed bindings in the parent and sibling facades. */
@Qualifier
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.VALUE_PARAMETER)
internal annotation class LegacyServiceBinding(val value: String)

/**
 * Factory ports are read independently by the facade instance, never captured in UserLifetime.
 * Session mapping: repository names -> the matching session getters; selfTeamIdProvider -> selfTeamId.
 * Read teamRepository independently of TeamScope and cached conversation/user captures.
 */
internal interface LegacyServiceDependencies {
    val serviceRepository: ServiceRepository
    val teamRepository: TeamRepository
    val userConfigRepository: UserConfigRepository
    val selfTeamIdProvider: SelfTeamIdProvider
}

/** Individual entry points; capture accessors also preserve explicit legacy aliases. */
internal interface LegacyServiceApi {
    @get:LegacyServiceBinding("serviceRepository")
    val serviceServiceRepository: ServiceRepository

    @get:LegacyServiceBinding("teamRepository")
    val serviceTeamRepository: TeamRepository

    @get:LegacyServiceBinding("userConfigRepository")
    val serviceUserConfigRepository: UserConfigRepository

    @get:LegacyServiceBinding("selfTeamIdProvider")
    val serviceSelfTeamIdProvider: SelfTeamIdProvider

    @get:LegacyServiceBinding("getServiceById")
    val serviceGetServiceById: GetServiceByIdUseCase

    @get:LegacyServiceBinding("observeIsServiceMember")
    val serviceObserveIsServiceMember: ObserveIsServiceMemberUseCase

    @get:LegacyServiceBinding("observeAllServices")
    val serviceObserveAllServices: ObserveAllServicesUseCase

    @get:LegacyServiceBinding("syncServices")
    val serviceSyncServices: SyncServicesUseCase

    @get:LegacyServiceBinding("searchServicesByName")
    val serviceSearchServicesByName: SearchServicesByNameUseCase

    @get:LegacyServiceBinding("observeIsAppsAllowedForUsage")
    val serviceObserveIsAppsAllowedForUsage: ObserveIsAppsAllowedForUsageUseCase
}

/** Per-instance capture providers and individual, unscoped use-case constructors. */
internal interface LegacyServiceBindings : LegacyServiceApi {
    @Provides
    @SingleIn(LegacyFacadeLifetime::class)
    @LegacyServiceBinding("serviceRepository")
    fun provideServiceServiceRepository(dependencies: LegacyServiceDependencies): ServiceRepository =
        dependencies.serviceRepository

    @Provides
    @SingleIn(LegacyFacadeLifetime::class)
    @LegacyServiceBinding("teamRepository")
    fun provideServiceTeamRepository(dependencies: LegacyServiceDependencies): TeamRepository =
        dependencies.teamRepository

    @Provides
    @SingleIn(LegacyFacadeLifetime::class)
    @LegacyServiceBinding("userConfigRepository")
    fun provideServiceUserConfigRepository(dependencies: LegacyServiceDependencies): UserConfigRepository =
        dependencies.userConfigRepository

    @Provides
    @SingleIn(LegacyFacadeLifetime::class)
    @LegacyServiceBinding("selfTeamIdProvider")
    fun provideServiceSelfTeamIdProvider(dependencies: LegacyServiceDependencies): SelfTeamIdProvider =
        dependencies.selfTeamIdProvider

    @Provides
    @LegacyServiceBinding("getServiceById")
    fun provideServiceGetServiceById(
        @LegacyServiceBinding("serviceRepository") serviceRepository: ServiceRepository,
    ): GetServiceByIdUseCase = GetServiceByIdUseCaseImpl(
        serviceRepository,
    )

    @Provides
    @LegacyServiceBinding("observeIsServiceMember")
    fun provideServiceObserveIsServiceMember(
        @LegacyServiceBinding("serviceRepository") serviceRepository: ServiceRepository,
    ): ObserveIsServiceMemberUseCase = ObserveIsServiceMemberUseCaseImpl(
        serviceRepository,
    )

    @Provides
    @LegacyServiceBinding("observeAllServices")
    fun provideServiceObserveAllServices(
        @LegacyServiceBinding("serviceRepository") serviceRepository: ServiceRepository,
    ): ObserveAllServicesUseCase = ObserveAllServicesUseCaseImpl(
        serviceRepository,
    )

    @Provides
    @LegacyServiceBinding("syncServices")
    fun provideServiceSyncServices(
        @LegacyServiceBinding("teamRepository") teamRepository: TeamRepository,
        @LegacyServiceBinding("selfTeamIdProvider") selfTeamIdProvider: SelfTeamIdProvider,
    ): SyncServicesUseCase = SyncServicesUseCaseImpl(
        teamRepository,
        selfTeamIdProvider,
    )

    @Provides
    @LegacyServiceBinding("searchServicesByName")
    fun provideServiceSearchServicesByName(
        @LegacyServiceBinding("serviceRepository") serviceRepository: ServiceRepository,
    ): SearchServicesByNameUseCase = SearchServicesByNameUseCaseImpl(
        serviceRepository,
    )

    @Provides
    @LegacyServiceBinding("observeIsAppsAllowedForUsage")
    fun provideServiceObserveIsAppsAllowedForUsage(
        @LegacyServiceBinding("userConfigRepository") userConfigRepository: UserConfigRepository,
        @LegacyServiceBinding("selfTeamIdProvider") selfTeamIdProvider: SelfTeamIdProvider,
    ): ObserveIsAppsAllowedForUsageUseCase = ObserveIsAppsAllowedForUsageUseCaseImpl(
        userConfigRepository,
        selfTeamIdProvider,
    )
}

/**
 * One extension per ServiceScope instance, generated at the final platform root.
 * Expose only this factory from the user graph; create a new extension on every facade getter.
 */
@GraphExtension(LegacyFacadeLifetime::class)
internal interface LegacyServiceFacadeGraph : LegacyServiceBindings {
    @GraphExtension.Factory
    interface Factory {
        fun create(@Provides dependencies: LegacyServiceDependencies): LegacyServiceFacadeGraph
    }
}
