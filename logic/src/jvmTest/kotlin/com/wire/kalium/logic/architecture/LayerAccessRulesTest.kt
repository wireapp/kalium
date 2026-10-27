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
package com.wire.kalium.logic.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.ext.list.withPackage
import com.lemonappdev.konsist.api.verify.assertFalse
import kotlin.test.Test
import kotlin.test.assertTrue

class LayerAccessRulesTest {

    @Test
    fun foundationalCoreModulesShouldNotImportInfrastructure() {
        val infrastructurePackages = listOf(
            "com.wire.kalium.network.",
            "com.wire.kalium.persistence.",
            "com.wire.kalium.cryptography.",
            "com.wire.crypto."
        )
        listOf("core/common", "core/data", "core/failures").forEach { module ->
            val files = Konsist.scopeFromProduction(moduleName = module).files
            assertTrue(files.isNotEmpty(), "Expected production sources in $module")
            files.assertFalse { file ->
                file.hasImport { imported ->
                    infrastructurePackages.any { imported.name.startsWith(it) } ||
                        imported.name == "com.wire.kalium.common.logger.CoreLogger"
                }
            }
        }
    }

    private companion object {
        val importsFromFeatureLayer = ".*?(\\.|)feature(\\..*|\\n)".toRegex()
        val importsFromPersistenceLayer = ".*?(\\.|)persistence(\\..*|\\n)".toRegex()
        val importsFromNetworkLayer = ".*?(\\.|)network(\\..*|\\n)".toRegex()
    }

    @Test
    fun repositoriesShouldNotAccessFeaturePackageClasses() {
        Konsist.scopeFromProduction()
            .files
            .withPackage("com.wire.kalium.logic.data..")
            .assertFalse {
                it.hasImport {
                    it.hasNameMatching(importsFromFeatureLayer)
                }
            }
    }

    @Test
    fun useCasesShouldNotAccessDaoLayerDirectly() {
        Konsist.scopeFromProduction()
            .files
            .withPackage("com.wire.kalium.logic.feature..")
            .filter { !it.hasNameEndingWith("Scope") }
            .assertFalse {
                it.hasImport {
                    it.hasNameMatching(importsFromPersistenceLayer)
                }
            }
    }

    @Test
    fun useCasesShouldNotAccessNetworkLayerDirectly() {
        Konsist.scopeFromProduction()
            .files
            .withPackage("com.wire.kalium.logic.feature..")
            .assertFalse {
                it.hasImport {
                    it.hasNameMatching(importsFromNetworkLayer) && !it.hasNameContaining("exception")
                }
            }
    }
}
