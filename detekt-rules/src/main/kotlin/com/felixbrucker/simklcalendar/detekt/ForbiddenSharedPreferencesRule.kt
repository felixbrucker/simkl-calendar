package com.felixbrucker.simklcalendar.detekt

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.KtUserType

class ForbiddenSharedPreferencesRule(config: Config = Config.empty) : Rule(config) {
  override val issue = Issue(
    id = "ForbiddenSharedPreferences",
    severity = Severity.Defect,
    description = "Prohibits usage of SharedPreferences in favor of DataStore Preferences.",
    debt = Debt.TEN_MINS
  )

  override fun visitImportDirective(importDirective: KtImportDirective) {
    super.visitImportDirective(importDirective)

    val path = importDirective.importedFqName?.asString() ?: return
    if (path.endsWith("SharedPreferencesMigration")) return
    if (path.contains("android.content.SharedPreferences") || path.contains("PreferenceManager")) {
      report(
        CodeSmell(
          issue = issue,
          entity = Entity.from(importDirective),
          message = "Usage of '$path' is prohibited. Use Jetpack DataStore Preferences instead."
        )
      )
    }
  }

  override fun visitUserType(type: KtUserType) {
    super.visitUserType(type)

    if (type.referencedName == "SharedPreferences") {
      report(
        CodeSmell(
          issue = issue,
          entity = Entity.from(type),
          message = "SharedPreferences is prohibited. Use Jetpack DataStore Preferences instead."
        )
      )
    }
  }
}
