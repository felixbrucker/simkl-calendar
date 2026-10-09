package com.felixbrucker.simklcalendar.detekt

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.psi.KtNamedFunction

class ComposableLineLimitRule(config: Config = Config.empty) : Rule(config) {
  override val issue = Issue(
    id = "ComposableLineLimit",
    severity = Severity.CodeSmell,
    description = "Enforces line limit (15 lines max) on @Composable helper functions.",
    debt = Debt.TEN_MINS
  )

  private val maxLines = 15

  override fun visitNamedFunction(function: KtNamedFunction) {
    super.visitNamedFunction(function)

    val isComposable = function.annotationEntries.any {
      it.shortName?.asString() == "Composable"
    }
    if (!isComposable) return

    val bodyBlock = function.bodyBlockExpression ?: return
    val text = bodyBlock.text.trim()
    val lines = text.lines().filter { it.isNotBlank() }
    val lineCount = lines.size - 2 // Exclude opening and closing braces

    if (lineCount > maxLines) {
      report(
        CodeSmell(
          issue = issue,
          entity = Entity.from(function),
          message = "Composable function '${function.name}' body has $lineCount code lines, exceeding the 15-line limit. Extract layout sections into separate composables."
        )
      )
    }
  }
}
