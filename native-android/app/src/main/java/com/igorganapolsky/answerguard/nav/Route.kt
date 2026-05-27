package com.igorganapolsky.answerguard.nav

import kotlinx.serialization.Serializable

/**
 * Type-safe nav-compose routes for AnswerGuard.
 *
 * Pattern is the 2026 idiomatic form (navigation-compose 2.8+):
 * each destination is a `@Serializable` `data object` / `data class`,
 * and the NavHost dispatches via `composable<Route.X>` without string
 * literals. Required args become path segments; optional args become
 * query params.
 *
 * Reference: https://developer.android.com/guide/navigation/design/type-safety
 */
sealed interface Route {

    /** Default landing — Recent Activity, Pro card, Privacy card. */
    @Serializable
    data object Home : Route

    /** Manage blocklist — add/remove numbers. */
    @Serializable
    data object Blocklist : Route

    /** App settings — How it Works, Privacy card, debug. */
    @Serializable
    data object Settings : Route
}
