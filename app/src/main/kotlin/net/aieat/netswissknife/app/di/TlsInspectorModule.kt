package net.aieat.netswissknife.app.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.aieat.netswissknife.core.domain.TlsInspectorUseCase
import net.aieat.netswissknife.core.network.tls.TlsInspectorRepository
import net.aieat.netswissknife.core.network.tls.TlsInspectorRepositoryImpl
import net.aieat.netswissknife.core.network.net.NetworkBinder
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object TlsInspectorModule {

    @Provides
    @Singleton
    fun provideTlsInspectorRepository(networkBinder: NetworkBinder): TlsInspectorRepository =
        TlsInspectorRepositoryImpl(networkBinder)

    @Provides
    @Singleton
    fun provideTlsInspectorUseCase(repo: TlsInspectorRepository): TlsInspectorUseCase =
        TlsInspectorUseCase(repo)
}
