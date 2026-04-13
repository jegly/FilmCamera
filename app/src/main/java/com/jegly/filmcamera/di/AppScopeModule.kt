package com.jegly.filmcamera.di

import android.content.Context
import androidx.work.WorkManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Qualifier for a [CoroutineScope] whose lifetime matches the Application process.
 * Use this instead of [androidx.lifecycle.ViewModel.viewModelScope] for work that must
 * survive ViewModel destruction (e.g. background image processing after the user closes
 * the app).
 */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class AppScope

@Module
@InstallIn(SingletonComponent::class)
object AppScopeModule {

    @Provides
    @Singleton
    @AppScope
    fun provideAppScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager =
        WorkManager.getInstance(context)
}
