package com.felixbrucker.simklcalendar.data.sentry

import android.content.Context
import com.felixbrucker.simklcalendar.di.SentryDsn
import io.sentry.Sentry
import io.sentry.SentryLevel
import io.sentry.android.core.SentryAndroid
import io.sentry.android.timber.SentryTimberIntegration
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SentryManager @Inject constructor(
    @SentryDsn private val dsn: String
) {
    val isDsnConfigured: Boolean get() = dsn.isNotBlank()
    val isSentryRunning: Boolean get() = Sentry.isEnabled()

    fun updateSentryState(context: Context, isEnabled: Boolean) {
        if (!isDsnConfigured) {
            Timber.i("Sentry DSN is not configured, not initializing.")
            return
        }

        if (!isSentryRunning && isEnabled) {
            SentryAndroid.init(context) { options ->
                options.dsn = dsn
                options.isEnabled = true

                // Enable screenshot for crashes
                options.isAttachScreenshot = true

                // Sample 100% of errors
                options.sampleRate = 1.0

                // Enable automatic traces for user interactions
                options.isEnableUserInteractionTracing = true
                options.isEnableUserInteractionBreadcrumbs = true

                // Enable view hierarchy for crashes
                options.isAttachViewHierarchy = true

                // Record session replays for 100% of errors
                options.sessionReplay.onErrorSampleRate = 1.0

                // Enable logs to be sent to Sentry
                options.addIntegration(
                    SentryTimberIntegration(
                        minEventLevel = SentryLevel.ERROR,
                        minBreadcrumbLevel = SentryLevel.INFO
                    )
                )

                // Enable tombstone support for richer Native crashes context
                options.isTombstoneEnabled = true

                options.isEnableUncaughtExceptionHandler = true
            }
            Timber.i("Sentry initialized")

            return
        }
    }
}
