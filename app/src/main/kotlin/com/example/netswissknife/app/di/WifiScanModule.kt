package com.example.netswissknife.app.di

import android.content.Context
import com.example.netswissknife.app.wifi.WifiScanRepositoryImpl
import com.example.netswissknife.core.domain.WifiScanUseCase
import com.example.netswissknife.core.network.wifi.WifiScanRepository
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
