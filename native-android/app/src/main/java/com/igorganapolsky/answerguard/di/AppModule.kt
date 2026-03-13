package com.igorganapolsky.answerguard.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.igorganapolsky.answerguard.data.SoundPreviewManagerImpl
import com.igorganapolsky.answerguard.data.repository.TimerRepositoryImpl
import com.igorganapolsky.answerguard.domain.SoundPreviewManager
import com.igorganapolsky.answerguard.domain.repository.TimerRepository
import com.igorganapolsky.answerguard.service.TimerServiceController
import com.igorganapolsky.answerguard.service.TimerServiceControllerImpl
import com.igorganapolsky.answerguard.stats.TrainingStatsService
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton
import kotlin.random.Random

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> =
        PreferenceDataStoreFactory.create {
            context.preferencesDataStoreFile("timer_preferences")
        }

    @Provides
    @Singleton
    fun provideTrainingStatsService(
        @ApplicationContext context: Context,
    ): TrainingStatsService = TrainingStatsService(context)

    @Provides
    @Singleton
    fun provideRandom(): Random = Random.Default

    @Provides
    @Singleton
    fun provideCoroutineScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindTimerRepository(impl: TimerRepositoryImpl): TimerRepository

    @Binds
    @Singleton
    abstract fun bindSoundPreviewManager(impl: SoundPreviewManagerImpl): SoundPreviewManager

    @Binds
    @Singleton
    abstract fun bindTimerServiceController(impl: TimerServiceControllerImpl): TimerServiceController
}
