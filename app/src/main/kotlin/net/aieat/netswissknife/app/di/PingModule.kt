package net.aieat.netswissknife.app.di

import net.aieat.netswissknife.core.domain.PingUseCase
import net.aieat.netswissknife.core.network.ping.PingRepository
import net.aieat.netswissknife.core.network.ping.PingRepositoryImpl
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
