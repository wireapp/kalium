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

import com.tngtech.archunit.base.DescribedPredicate
import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.ArchCondition
import com.tngtech.archunit.lang.ConditionEvents
import com.tngtech.archunit.lang.SimpleConditionEvent
import kotlin.reflect.KVisibility

internal object ArchTestingSetup {

    var logicImportedClasses: JavaClasses = ClassFileImporter()
        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
        .importPackages("com.wire.kalium.logic")
}


fun isKotlinInternal() = object : DescribedPredicate<JavaClass>("Kotlin internal class") {
    override fun apply(input: JavaClass) = input.reflect().isKotlinInternal()

    private fun Class<*>.isKotlinInternal() = isKotlinClass() && isInternal()

}

fun Class<*>.isInternal() = this.kotlin.visibility == KVisibility.INTERNAL

private fun Class<*>.isKotlinClass() = this.declaredAnnotations.any {
    it.annotationClass.qualifiedName == "kotlin.Metadata"
}

internal fun HaveInternalVisibilityOnly() = object : ArchCondition<JavaClass>("Be Kotlin internal class") {

    override fun check(item: JavaClass?, events: ConditionEvents?) {
        val onlyInternalModifier = isKotlinInternal().apply(item)
        if (!onlyInternalModifier) {
            events!!.add(
                SimpleConditionEvent(
                    item,
                    false,
                    "class: ${item!!.name} should only be possible to instantiate internally"
                )
            )
        }
    }
}
