package com.example.netswissknife.app.di

import com.example.netswissknife.core.domain.LanScanUseCase
import com.example.netswissknife.core.network.lan.LanScanRepository
import com.example.netswissknife.core.network.lan.LanScanRepositoryImpl
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
    fun provideLanScanRepository(): LanScanRepository = LanScanRepositoryImpl()

    @Provides
    @Singleton
    fun provideLanScanUseCase(repository: LanScanRepository): LanScanUseCase =
        LanScanUseCase(repository)
}
