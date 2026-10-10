package com.felixbrucker.simklcalendar.detekt

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtConstructor
import org.jetbrains.kotlin.psi.KtPrimaryConstructor
import org.jetbrains.kotlin.psi.KtSecondaryConstructor

class NoDefaultParamsInInjectableConstructorRule(config: Config = Config.empty) : Rule(config) {
  override val issue = Issue(
    id = "NoDefaultParamsInInjectableConstructor",
    severity = Severity.Defect,
    description = "Prohibits default parameter values in @Inject annotated constructors.",
    debt = Debt.FIVE_MINS
  )

  override fun visitPrimaryConstructor(constructor: KtPrimaryConstructor) {
    super.visitPrimaryConstructor(constructor)
    checkConstructor(constructor)
  }

  override fun visitSecondaryConstructor(constructor: KtSecondaryConstructor) {
    super.visitSecondaryConstructor(constructor)
    checkConstructor(constructor)
  }

  private fun checkConstructor(constructor: KtConstructor<*>) {
    val isInject = constructor.annotationEntries.any {
      it.shortName?.asString() == "Inject"
    } || (constructor.parent as? KtClass)?.annotationEntries?.any {
      it.shortName?.asString() == "Inject"
    } == true

    if (!isInject) return

    for (param in constructor.valueParameters) {
      if (param.hasDefaultValue()) {
        report(
          CodeSmell(
            issue = issue,
            entity = Entity.from(param),
            message = "Parameter '${param.name}' in @Inject constructor has a default value. Default parameter values in injectable constructors are prohibited."
          )
        )
      }
    }
  }
}
