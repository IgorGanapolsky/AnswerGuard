package com.igorganapolsky.answerguard

import android.app.Application
import com.igorganapolsky.answerguard.analytics.AnalyticsService
import com.google.firebase.analytics.FirebaseAnalytics
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class AnswerGuardApp : Application() {

    @Inject lateinit var analyticsService: AnalyticsService

    override fun onCreate() {
        super.onCreate()

        // PostHog is our source of truth for product analytics.
        // Disable Firebase Analytics event collection to avoid duplicate telemetry streams.
        runCatching {
            FirebaseAnalytics.getInstance(this).setAnalyticsCollectionEnabled(false)
        }

        analyticsService.initialize(this)
    }
}
