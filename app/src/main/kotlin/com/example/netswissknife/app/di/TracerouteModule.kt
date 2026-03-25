package com.example.netswissknife.app.di

import com.example.netswissknife.app.traceroute.IcmpEnginTracerouteRepositoryImpl
import com.example.netswissknife.core.domain.TracerouteUseCase
import com.example.netswissknife.core.network.traceroute.GeoIpRepository
import com.example.netswissknife.core.network.traceroute.GeoIpRepositoryImpl
import com.example.netswissknife.core.network.traceroute.TracerouteRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object TracerouteModule {

    @Provides
    @Singleton
    fun provideTracerouteRepository(): TracerouteRepository = IcmpEnginTracerouteRepositoryImpl()

    @Provides
    @Singleton
    fun provideGeoIpRepository(): GeoIpRepository = GeoIpRepositoryImpl()

    @Provides
    @Singleton
    fun provideTracerouteUseCase(
        tracerouteRepository: TracerouteRepository,
        geoIpRepository: GeoIpRepository
    ): TracerouteUseCase = TracerouteUseCase(tracerouteRepository, geoIpRepository)
}
