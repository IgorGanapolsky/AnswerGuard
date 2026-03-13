# AnswerGuard ProGuard Rules

# Keep Hilt generated classes
-keepclasseswithmembers class * {
    @dagger.hilt.* <methods>;
}

# Keep Kotlin Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# Keep data classes for DataStore
-keep class com.igorganapolsky.answerguard.domain.model.** { *; }

# Keep Compose
-keep class androidx.compose.** { *; }

# Keep Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
