package net.aieat.netswissknife.app.di

import net.aieat.netswissknife.core.domain.PortScanUseCase
import net.aieat.netswissknife.core.network.portscan.PortScanRepository
import net.aieat.netswissknife.core.network.portscan.PortScanRepositoryImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PortScanModule {

    @Provides
    @Singleton
    fun providePortScanRepository(): PortScanRepository = PortScanRepositoryImpl()

    @Provides
    @Singleton
    fun providePortScanUseCase(repository: PortScanRepository): PortScanUseCase =
        PortScanUseCase(repository)
}
