package com.example.netswissknife.app.di

import com.example.netswissknife.core.domain.PingUseCase
import com.example.netswissknife.core.network.ping.PingRepository
import com.example.netswissknife.core.network.ping.PingRepositoryImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PingModule {

    @Provides
    @Singleton
    fun providePingRepository(): PingRepository = PingRepositoryImpl()

    @Provides
    @Singleton
    fun providePingUseCase(repository: PingRepository): PingUseCase =
        PingUseCase(repository)
}
