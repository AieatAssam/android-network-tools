package net.aieat.netswissknife.app.di

import android.content.Context
import net.aieat.netswissknife.app.wifi.WifiScanRepositoryImpl
import net.aieat.netswissknife.core.domain.WifiScanUseCase
import net.aieat.netswissknife.core.network.wifi.WifiScanRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object WifiScanModule {

    @Provides
    @Singleton
    fun provideWifiScanRepository(
        @ApplicationContext context: Context
    ): WifiScanRepository = WifiScanRepositoryImpl(context)

    @Provides
    @Singleton
    fun provideWifiScanUseCase(repository: WifiScanRepository): WifiScanUseCase =
        WifiScanUseCase(repository)
}
