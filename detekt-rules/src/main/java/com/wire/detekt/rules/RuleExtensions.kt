package com.wire.detekt.rules

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Rule
import org.jetbrains.kotlin.psi.KtClassOrObject

fun Rule.report(classOrObject: KtClassOrObject, message: String) {
    report(CodeSmell(issue, Entity.atName(classOrObject), message))
}
