package net.aieat.netswissknife.app.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.aieat.netswissknife.core.domain.SpeedTestUseCase
import net.aieat.netswissknife.core.network.speedtest.SpeedTestRepository
import net.aieat.netswissknife.core.network.speedtest.SpeedTestRepositoryImpl
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SpeedTestModule {

    @Provides
    @Singleton
    fun provideSpeedTestRepository(): SpeedTestRepository = SpeedTestRepositoryImpl()

    @Provides
    @Singleton
    fun provideSpeedTestUseCase(repository: SpeedTestRepository): SpeedTestUseCase =
        SpeedTestUseCase(repository)
}
