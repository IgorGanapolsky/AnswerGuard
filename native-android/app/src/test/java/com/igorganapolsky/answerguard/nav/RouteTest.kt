package com.igorganapolsky.answerguard.nav

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.Test

/**
 * Compile-time contracts for the [Route] sealed interface that powers the
 * NavHost in MainActivity. The actual NavController behaviour (start
 * destination, deep-link resolution, back-stack pop) is exercised by Maestro
 * device flows; these tests catch the things that would break the migration
 * at runtime *before* a device round-trip:
 *
 * - Every concrete [Route] survives Kotlin-serialization round-trip. If
 *   `@Serializable` is silently dropped from any data object/class,
 *   nav-compose's type-safe `composable<Route.X>` would fail to register
 *   the destination and the app would crash on first navigate.
 *
 * - The known route set is exactly the three the migration ships. Adding a
 *   new route requires updating this test, which is the tripwire we want.
 */
class RouteTest {

    private val json = Json { allowStructuredMapKeys = true }

    @Test
    fun `Route Home is serializable and survives round-trip`() {
        val encoded = json.encodeToString(Route.Home.serializer(), Route.Home)
        val decoded = json.decodeFromString(Route.Home.serializer(), encoded)
        assertThat(decoded).isEqualTo(Route.Home)
    }

    @Test
    fun `Route Blocklist is serializable and survives round-trip`() {
        val encoded = json.encodeToString(Route.Blocklist.serializer(), Route.Blocklist)
        val decoded = json.decodeFromString(Route.Blocklist.serializer(), encoded)
        assertThat(decoded).isEqualTo(Route.Blocklist)
    }

    @Test
    fun `Route Settings is serializable and survives round-trip`() {
        val encoded = json.encodeToString(Route.Settings.serializer(), Route.Settings)
        val decoded = json.decodeFromString(Route.Settings.serializer(), encoded)
        assertThat(decoded).isEqualTo(Route.Settings)
    }

    @Test
    fun `Route hierarchy is exactly three known destinations`() {
        // Reflection-based assertion so adding a new Route fails this test
        // until the dev confirms it's intentional.
        val sealedSubclasses = Route::class.sealedSubclasses
            .map { it.simpleName }
            .toSet()
        assertThat(sealedSubclasses).containsExactly("Home", "Blocklist", "Settings")
    }

    @Test
    fun `Route data objects are reference-equal across decodes`() {
        // data objects are singletons; serialization must preserve identity.
        val homeJson = json.encodeToString(Route.Home.serializer(), Route.Home)
        val home1 = json.decodeFromString(Route.Home.serializer(), homeJson)
        val home2 = json.decodeFromString(Route.Home.serializer(), homeJson)
        assertThat(home1).isSameInstanceAs(home2)
        assertThat(home1).isSameInstanceAs(Route.Home)
    }
}
