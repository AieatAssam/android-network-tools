package net.aieat.netswissknife.app.di

import net.aieat.netswissknife.core.domain.ContinuousPingUseCase
import net.aieat.netswissknife.core.domain.PingUseCase
import net.aieat.netswissknife.core.network.ping.PingRepository
import net.aieat.netswissknife.core.network.ping.PingRepositoryImpl
import net.aieat.netswissknife.core.network.ping.PingEngine
import net.aieat.netswissknife.core.network.ping.ReachabilityPingEngine
import net.aieat.netswissknife.app.ping.IcmpenguinPingEngine
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
    fun providePingRepository(): PingRepository = PingRepositoryImpl(
        engines = listOf<PingEngine>(IcmpenguinPingEngine(), ReachabilityPingEngine())
    )

    @Provides
    @Singleton
    fun providePingUseCase(repository: PingRepository): PingUseCase =
        PingUseCase(repository)

    @Provides
    @Singleton
    fun provideContinuousPingUseCase(repository: PingRepository): ContinuousPingUseCase =
        ContinuousPingUseCase(repository)
}
