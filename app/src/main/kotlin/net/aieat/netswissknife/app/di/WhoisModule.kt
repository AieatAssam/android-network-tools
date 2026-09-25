package net.aieat.netswissknife.app.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.aieat.netswissknife.core.domain.WhoisLookupUseCase
import net.aieat.netswissknife.core.network.whois.RdapClient
import net.aieat.netswissknife.core.network.whois.WhoisRepository
import net.aieat.netswissknife.core.network.whois.WhoisRepositoryImpl
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object WhoisModule {

    @Provides
    @Singleton
    fun provideRdapClient(): RdapClient = RdapClient()

    @Provides
    @Singleton
    fun provideWhoisRepository(rdapClient: RdapClient): WhoisRepository =
        WhoisRepositoryImpl(rdapClient = rdapClient)

    @Provides
    @Singleton
    fun provideWhoisLookupUseCase(repo: WhoisRepository): WhoisLookupUseCase =
        WhoisLookupUseCase(repo)
}
