package net.aieat.netswissknife.app.di

import net.aieat.netswissknife.core.domain.DnsLookupUseCase
import net.aieat.netswissknife.core.network.dns.DnsRepository
import net.aieat.netswissknife.core.network.dns.DnsRepositoryImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DnsModule {

    @Provides
    @Singleton
    fun provideDnsRepository(): DnsRepository = DnsRepositoryImpl()

    @Provides
    @Singleton
    fun provideDnsLookupUseCase(repository: DnsRepository): DnsLookupUseCase =
        DnsLookupUseCase(repository)
}
