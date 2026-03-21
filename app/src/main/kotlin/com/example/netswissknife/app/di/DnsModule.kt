package com.example.netswissknife.app.di

import com.example.netswissknife.core.domain.DnsLookupUseCase
import com.example.netswissknife.core.network.dns.DnsRepository
import com.example.netswissknife.core.network.dns.DnsRepositoryImpl
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
