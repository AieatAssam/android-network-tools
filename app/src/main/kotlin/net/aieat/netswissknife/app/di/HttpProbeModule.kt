package net.aieat.netswissknife.app.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.aieat.netswissknife.core.domain.HttpProbeUseCase
import net.aieat.netswissknife.core.network.httprobe.HttpProbeRepository
import net.aieat.netswissknife.core.network.httprobe.HttpProbeRepositoryImpl
import net.aieat.netswissknife.core.network.net.NetworkBinder
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object HttpProbeModule {

    @Provides
    @Singleton
    fun provideHttpProbeRepository(networkBinder: NetworkBinder): HttpProbeRepository =
        HttpProbeRepositoryImpl(networkBinder)

    @Provides
    @Singleton
    fun provideHttpProbeUseCase(repo: HttpProbeRepository): HttpProbeUseCase =
        HttpProbeUseCase(repo)
}
