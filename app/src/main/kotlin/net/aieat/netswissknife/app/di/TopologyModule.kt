package net.aieat.netswissknife.app.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.aieat.netswissknife.core.domain.TopologyDiscoveryUseCase
import net.aieat.netswissknife.core.network.topology.Snmp4jClientImpl
import net.aieat.netswissknife.core.network.topology.TopologyDiscoveryRepository
import net.aieat.netswissknife.core.network.topology.TopologyDiscoveryRepositoryImpl
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object TopologyModule {

    @Provides
    @Singleton
    fun provideTopologyDiscoveryRepository(): TopologyDiscoveryRepository =
        TopologyDiscoveryRepositoryImpl(Snmp4jClientImpl())

    @Provides
    @Singleton
    fun provideTopologyDiscoveryUseCase(
        repository: TopologyDiscoveryRepository
    ): TopologyDiscoveryUseCase = TopologyDiscoveryUseCase(repository)
}
