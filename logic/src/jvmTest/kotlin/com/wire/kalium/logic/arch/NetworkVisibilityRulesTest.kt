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
package com.wire.kalium.logic.arch

import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import org.junit.Test


class NetworkVisibilityRulesTest {

    /**
     * network classes should be internal.
     */
    @Test
    fun networkModuleClassesShouldBeClosedAsInternally() {
        classes()
            .that()
            .resideInAPackage("..network..")
            .should(HaveInternalVisibilityOnly())
            .check(ArchTestingSetup.importedClasses)
    }
}


