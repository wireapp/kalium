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
package com.wire.kalium.logic.feature.app

import com.wire.kalium.logic.data.app.AppRepository
import com.wire.kalium.logic.data.id.SelfTeamIdProvider
import com.wire.kalium.logic.feature.LegacyFacadeLifetime
import dev.zacsweers.metro.GraphExtension
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.Qualifier
import dev.zacsweers.metro.SingleIn

/** Distinguishes this facade's captures from same-typed bindings in the parent and sibling facades. */
@Qualifier
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.VALUE_PARAMETER)
internal annotation class LegacyAppBinding(val value: String)

/**
 * Factory ports are read independently by the facade instance, never captured in UserLifetime.
 * Session mapping: appRepository -> the session getter; selfTeamIdProvider -> selfTeamId.
 * The factory takes the API version VALUE read by each session.apps access, never a live provider.
 */
internal interface LegacyAppDependencies {
    val appRepository: AppRepository
    val selfTeamIdProvider: SelfTeamIdProvider
}

/** Individual entry points; capture accessors also preserve explicit legacy aliases. */
internal interface LegacyAppApi {
    @get:LegacyAppBinding("appRepository")
    val appAppRepository: AppRepository

    @get:LegacyAppBinding("selfTeamIdProvider")
    val appSelfTeamIdProvider: SelfTeamIdProvider

    @get:LegacyAppBinding("apiVersion")
    val appApiVersion: Int

    @get:LegacyAppBinding("syncApps")
    val appSyncApps: SyncAppsUseCase

    @get:LegacyAppBinding("getAppById")
    val appGetAppById: GetAppByIdUseCase

    @get:LegacyAppBinding("observeIsAppMember")
    val appObserveIsAppMember: ObserveIsAppMemberUseCase

    @get:LegacyAppBinding("observeAllApps")
    val appObserveAllApps: ObserveAllAppsUseCase

    @get:LegacyAppBinding("searchAppsByName")
    val appSearchAppsByName: SearchAppsByNameUseCase
}

/** Per-instance capture providers and individual, unscoped use-case constructors. */
internal interface LegacyAppBindings : LegacyAppApi {
    @Provides
    @SingleIn(LegacyFacadeLifetime::class)
    @LegacyAppBinding("appRepository")
    fun provideAppAppRepository(dependencies: LegacyAppDependencies): AppRepository =
        dependencies.appRepository

    @Provides
    @SingleIn(LegacyFacadeLifetime::class)
    @LegacyAppBinding("selfTeamIdProvider")
    fun provideAppSelfTeamIdProvider(dependencies: LegacyAppDependencies): SelfTeamIdProvider =
        dependencies.selfTeamIdProvider

    @Provides
    @LegacyAppBinding("syncApps")
    fun provideAppSyncApps(
        @LegacyAppBinding("appRepository") appRepository: AppRepository,
        @LegacyAppBinding("selfTeamIdProvider") selfTeamIdProvider: SelfTeamIdProvider,
        @LegacyAppBinding("apiVersion") apiVersion: Int,
    ): SyncAppsUseCase = SyncAppsUseCaseImpl(
        appRepository,
        selfTeamIdProvider,
        apiVersion,
    )

    @Provides
    @LegacyAppBinding("getAppById")
    fun provideAppGetAppById(
        @LegacyAppBinding("appRepository") appRepository: AppRepository,
    ): GetAppByIdUseCase = GetAppByIdUseCaseImpl(
        appRepository,
    )

    @Provides
    @LegacyAppBinding("observeIsAppMember")
    fun provideAppObserveIsAppMember(
        @LegacyAppBinding("appRepository") appRepository: AppRepository,
    ): ObserveIsAppMemberUseCase = ObserveIsAppMemberUseCaseImpl(
        appRepository,
    )

    @Provides
    @LegacyAppBinding("observeAllApps")
    fun provideAppObserveAllApps(
        @LegacyAppBinding("appRepository") appRepository: AppRepository,
    ): ObserveAllAppsUseCase = ObserveAllAppsUseCaseImpl(
        appRepository,
    )

    @Provides
    @LegacyAppBinding("searchAppsByName")
    fun provideAppSearchAppsByName(
        @LegacyAppBinding("appRepository") appRepository: AppRepository,
    ): SearchAppsByNameUseCase = SearchAppsByNameUseCaseImpl(
        appRepository,
    )
}

/**
 * One extension per AppScope instance, generated at the final platform root.
 * Expose only this factory from the user graph; create a new extension on every facade getter.
 */
@GraphExtension(LegacyFacadeLifetime::class)
internal interface LegacyAppFacadeGraph : LegacyAppBindings {
    @GraphExtension.Factory
    interface Factory {
        fun create(
            @Provides dependencies: LegacyAppDependencies,
            @Provides @LegacyAppBinding("apiVersion") apiVersion: Int,
        ): LegacyAppFacadeGraph
    }
}
