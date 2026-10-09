package com.felixbrucker.simklcalendar.detekt

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.psi.KtNamedFunction

class StatelessChildComposableRule(config: Config = Config.empty) : Rule(config) {
  override val issue = Issue(
    id = "StatelessChildComposable",
    severity = Severity.CodeSmell,
    description = "Child composables must be stateless and must not receive ViewModel parameters directly.",
    debt = Debt.TEN_MINS
  )

  override fun visitNamedFunction(function: KtNamedFunction) {
    super.visitNamedFunction(function)

    val isComposable = function.annotationEntries.any {
      it.shortName?.asString() == "Composable"
    }
    if (!isComposable) return

    val functionName = function.name ?: return
    if (functionName.endsWith("Screen")) return

    for (param in function.valueParameters) {
      val typeReference = param.typeReference?.text ?: continue
      if (typeReference.endsWith("ViewModel")) {
        report(
          CodeSmell(
            issue = issue,
            entity = Entity.from(param),
            message = "Child composable '$functionName' receives ViewModel parameter '${param.name}: $typeReference'. Use state hoisting with raw data parameters instead."
          )
        )
      }
    }
  }
}
