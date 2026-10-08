package net.aieat.netswissknife.app.di

import net.aieat.netswissknife.app.lan.IcmpenguinIcmpProbe
import net.aieat.netswissknife.core.domain.LanScanUseCase
import net.aieat.netswissknife.core.network.lan.ArpFileMacResolver
import net.aieat.netswissknife.core.network.lan.IcmpProbe
import net.aieat.netswissknife.core.network.lan.LanScanRepository
import net.aieat.netswissknife.core.network.lan.LanScanRepositoryImpl
import net.aieat.netswissknife.core.network.net.NetworkBinder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object LanModule {

    @Provides
    @Singleton
    fun provideIcmpProbe(): IcmpProbe = IcmpenguinIcmpProbe()

    @Provides
    @Singleton
    fun provideLanScanRepository(icmpProbe: IcmpProbe, binder: NetworkBinder): LanScanRepository =
        LanScanRepositoryImpl(
            icmpProbe = icmpProbe,
            macResolver = ArpFileMacResolver(),
            binder = binder,
        )

    @Provides
    @Singleton
    fun provideLanScanUseCase(repository: LanScanRepository): LanScanUseCase =
        LanScanUseCase(repository)
}
