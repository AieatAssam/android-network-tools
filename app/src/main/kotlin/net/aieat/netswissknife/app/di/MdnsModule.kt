package net.aieat.netswissknife.app.di

import net.aieat.netswissknife.app.mdns.MdnsRepositoryImpl
import net.aieat.netswissknife.core.domain.MdnsDiscoveryUseCase
import net.aieat.netswissknife.core.network.mdns.MdnsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object MdnsModule {

    @Provides
    @Singleton
    fun provideMdnsRepository(impl: MdnsRepositoryImpl): MdnsRepository = impl

    @Provides
    @Singleton
    fun provideMdnsDiscoveryUseCase(repository: MdnsRepository): MdnsDiscoveryUseCase =
        MdnsDiscoveryUseCase(repository)
}
