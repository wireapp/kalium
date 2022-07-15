@file:Suppress("MatchingDeclarationName")
package com.wire.kalium.util


@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.ANNOTATION_CLASS,
    AnnotationTarget.CONSTRUCTOR,
    AnnotationTarget.PROPERTY_SETTER,
    AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.TYPEALIAS
)
@Retention(AnnotationRetention.BINARY)
@RequiresOptIn(
    level = RequiresOptIn.Level.WARNING,
    message = "This API is delicate — it may have limited use-case and shall be used with care in general code."
)
annotation class DelicateKaliumApi(
    val message: String,
    val replaceWith: ReplaceWith = ReplaceWith(""),
)
