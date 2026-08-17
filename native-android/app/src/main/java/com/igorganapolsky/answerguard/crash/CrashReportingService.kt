package com.igorganapolsky.answerguard.crash

import com.google.firebase.crashlytics.FirebaseCrashlytics
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CrashReportingService @Inject constructor() {

    private val crashlytics: FirebaseCrashlytics? by lazy {
        runCatching { FirebaseCrashlytics.getInstance() }.getOrNull()
    }

    fun initialize() {
        runCatching {
            crashlytics?.isCrashlyticsCollectionEnabled = true
        }
    }

    fun setUserId(userId: String) {
        crashlytics?.setUserId(userId)
    }

    fun log(message: String) {
        crashlytics?.log(message)
    }

    fun setCustomKey(key: String, value: String) {
        crashlytics?.setCustomKey(key, value)
    }

    fun recordException(throwable: Throwable) {
        crashlytics?.recordException(throwable)
    }
}
