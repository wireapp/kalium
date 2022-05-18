package com.wire.detekt.rules

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.RuleSet
import io.gitlab.arturbosch.detekt.api.RuleSetProvider

class WireRuleSetProvider : RuleSetProvider {

    override val ruleSetId: String = "WireRuleSet"

    override fun instance(config: Config) = RuleSet(ruleSetId, listOf(EnforceSerializerFields(config)))
}
