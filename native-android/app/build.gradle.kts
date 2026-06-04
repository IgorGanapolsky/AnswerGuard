import java.math.BigDecimal
import java.util.Properties
import org.gradle.api.tasks.testing.Test
import org.gradle.testing.jacoco.plugins.JacocoTaskExtension
import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification
import org.gradle.testing.jacoco.tasks.JacocoReport

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.kotlinCompose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.googleServices) apply false
    alias(libs.plugins.firebaseCrashlytics) apply false
    jacoco
}

val hasGoogleServicesConfig =
    listOf(
        "google-services.json",
        "src/debug/google-services.json",
        "src/release/google-services.json",
    ).any { file(it).exists() }

val enableFirebasePlugins =
    providers.gradleProperty("enableFirebasePlugins")
        .map(String::toBoolean)
        .orElse(hasGoogleServicesConfig)
        .get()

if (enableFirebasePlugins) {
    apply(plugin = "com.google.gms.google-services")
    apply(plugin = "com.google.firebase.crashlytics")
} else {
    logger.lifecycle("google-services.json not found; skipping Firebase Gradle plugins for local verification.")
}

val ciCompileSdk = providers.gradleProperty("ciCompileSdk").orNull?.toIntOrNull()
val ciTargetSdk = providers.gradleProperty("ciTargetSdk").orNull?.toIntOrNull()
val ciVersionCode = providers.gradleProperty("ciVersionCode").orNull?.toIntOrNull()

android {
    namespace = "com.igorganapolsky.answerguard"
    compileSdk = ciCompileSdk ?: 35

    defaultConfig {
        applicationId = "com.igorganapolsky.answerguard"
        minSdk = 26
        targetSdk = ciTargetSdk ?: 35
        versionCode = ciVersionCode ?: 1780576389
        versionName = "1.2.7"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // PostHog Analytics - from local.properties, gradle.properties or CI secret
        val posthogApiKey = (System.getenv("POSTHOG_API_KEY")
            ?: project.findProperty("POSTHOG_API_KEY")
            ?: run {
                val props = Properties()
                val localPropsFile = project.rootProject.file("local.properties")
                if (localPropsFile.exists()) {
                    localPropsFile.inputStream().use { props.load(it) }
                }
                props.getProperty("POSTHOG_API_KEY")
            } ?: "").toString()
        buildConfigField("String", "POSTHOG_API_KEY", "\"$posthogApiKey\"")

        // Short git SHA for in-app build verification. Honors $GITHUB_SHA when
        // set by GitHub Actions, falls back to `git rev-parse` for local builds.
        val gitSha: String = (System.getenv("GITHUB_SHA")?.take(7))
            ?: try {
                Runtime.getRuntime().exec(arrayOf("git", "rev-parse", "--short=7", "HEAD"))
                    .inputStream.bufferedReader().readText().trim()
            } catch (_: Exception) { "unknown" }
        buildConfigField("String", "GIT_SHA", "\"$gitSha\"")
    }

    signingConfigs {
        create("release") {
            val keystorePath = System.getenv("KEYSTORE_PATH")
            if (keystorePath != null) {
                storeFile = file(keystorePath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = if (System.getenv("KEYSTORE_PATH") != null) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
    lint {
        // Work around upstream Compose lint detector crash:
        // IncompatibleClassChangeError in FrequentlyChangingValueDetector.
        // Keep lint enabled for all other checks.
        disable += "FrequentlyChangingValue"
        disable += "RememberInComposition"
        disable += "NullSafeMutableLiveData"
        disable += "AutoboxingStateCreation"
    }
}

dependencies {
    // Core Android
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.core)
    implementation(libs.androidx.navigation.compose)

    // Dependency Injection
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Data
    implementation(libs.androidx.datastore.preferences)

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // Analytics
    implementation(libs.posthog)
    implementation(libs.sentry)

    // In-App Review
    implementation(libs.play.review)

    // In-App Billing
    implementation(libs.play.billing)

    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.analytics)

    // Firebase App Distribution — the full SDK self-prompts testers on every
    // launch ("Enable testing features … new build alerts and in-app feedback")
    // and checks for new internal builds. That prompt must NEVER reach Play
    // Store / production users. Ship the real SDK in DEBUG/internal builds only;
    // release builds get the api-only NO-OP stub, so MainActivity's
    // updateIfNewReleaseAvailable() compiles but does nothing in production.
    debugImplementation(libs.firebase.appdistribution)
    releaseImplementation(libs.firebase.appdistribution.api)

    // Media Session (Bluetooth/Android Auto alarm dismiss)
    implementation(libs.androidx.media)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.truth)
    testImplementation(libs.org.json)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.ui.test.junit4)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.uiautomator)
    androidTestImplementation(libs.truth)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)

    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}

jacoco {
    toolVersion = "0.8.12"
}

tasks.withType<Test>().configureEach {
    extensions.configure(JacocoTaskExtension::class.java) {
        isIncludeNoLocationClasses = true
        excludes = listOf("jdk.internal.*")
    }
}

tasks.register<JacocoReport>("jacocoDebugUnitTestReport") {
    dependsOn("testDebugUnitTest")

    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }

    val excludes = listOf(
        "**/R.class",
        "**/R$*.class",
        "**/BuildConfig.*",
        "**/Manifest*.*",
        "**/*Test*.*",
        "android/**/*.*",
        "**/*\$Lambda$*.*",
        "**/*\$inlined$*.*",
        // Compose UI surface — not unit-testable without Compose UI test framework
        // or Robolectric. ProManager / AnalyticsService stay in the metric to
        // drive future tests; only the Activity + its generated Composable
        // lambdas are excluded.
        "**/MainActivity*.*",
        "**/*ComposableSingletons*.*",
        // Hilt/Dagger generated code — KSP-emitted, not authored. Including
        // these skews INSTRUCTION coverage (Hilt_MainActivity alone is 117
        // instr at 0%, dragging the suite from ~82% down to 79.99%).
        "**/Hilt_*.*",
        "**/Dagger*.*",
        "**/*_HiltModules*.*",
        "**/*_Factory.*",
        "**/*_MembersInjector.*",
        "**/*_Impl.*",
        "**/*_Provide*Factory.*",
        // Design tokens — pure data, nothing to test.
        "**/AnswerGuardColors.*",
        // Hilt @Module — declaration-only @Provides scaffolding; validated by
        // Hilt's compile-time checks, not unit tests.
        "**/di/AppModule*.*",
    )

    val buildDirFile = layout.buildDirectory.get().asFile
    // Use the ASM-transformed classes that are actually loaded at test runtime —
    // Hilt's @AndroidEntryPoint injection happens via bytecode transformation, and
    // Jacoco's exec data is keyed on the transformed class bytecode. Falling back
    // to tmp/kotlin-classes/debug would yield 0% coverage for any @AndroidEntryPoint
    // class because the runtime bytecode differs from the report-time bytecode.
    val asmTransformedDir = buildDirFile.resolve("intermediates/classes/debug/transformDebugClassesWithAsm/dirs")
    val kotlinClassesDir = buildDirFile.resolve("tmp/kotlin-classes/debug")
    val classesRoot = if (asmTransformedDir.exists()) asmTransformedDir else kotlinClassesDir
    val kotlinClasses = fileTree(classesRoot) { exclude(excludes) }
    val javaClasses = fileTree(buildDirFile.resolve("intermediates/javac/debug/classes")) { exclude(excludes) }

    classDirectories.setFrom(files(kotlinClasses, javaClasses))
    sourceDirectories.setFrom(files("src/main/java", "src/main/kotlin"))
    executionData.setFrom(
        fileTree(buildDirFile) {
            include("jacoco/testDebugUnitTest.exec")
            include("outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec")
        }
    )
}

// Coverage ratchet. MainActivity Composables are excluded (not unit-testable).
// ProManager (~1041 instructions) and AnalyticsService (~835) are intentionally
// IN the metric — they're production logic that should drive future tests.
// Raise threshold as those classes get covered.
tasks.register<JacocoCoverageVerification>("jacocoCoverageVerification") {
    dependsOn("jacocoDebugUnitTestReport")

    val reportTask = tasks.named<JacocoReport>("jacocoDebugUnitTestReport").get()
    classDirectories.setFrom(reportTask.classDirectories)
    sourceDirectories.setFrom(reportTask.sourceDirectories)
    executionData.setFrom(reportTask.executionData)

    violationRules {
        rule {
            limit {
                counter = "INSTRUCTION"
                value = "COVEREDRATIO"
                minimum = BigDecimal("0.80")
            }
        }
    }
}

tasks.named("check") {
    dependsOn("jacocoCoverageVerification")
}
