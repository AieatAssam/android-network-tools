package net.aieat.netswissknife.app.di

import net.aieat.netswissknife.app.traceroute.IcmpEnginTracerouteRepositoryImpl
import net.aieat.netswissknife.app.traceroute.BoundedTracerouteReverseDnsLookup
import net.aieat.netswissknife.core.domain.TracerouteUseCase
import net.aieat.netswissknife.core.network.traceroute.GeoIpRepository
import net.aieat.netswissknife.core.network.traceroute.GeoIpRepositoryImpl
import net.aieat.netswissknife.core.network.traceroute.TracerouteRepository
import net.aieat.netswissknife.core.network.traceroute.TracerouteReverseDnsRepository
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
    fun provideTracerouteReverseDnsRepository(): TracerouteReverseDnsRepository =
        BoundedTracerouteReverseDnsLookup()

    @Provides
    @Singleton
    fun provideTracerouteUseCase(
        tracerouteRepository: TracerouteRepository,
        geoIpRepository: GeoIpRepository,
        reverseDnsRepository: TracerouteReverseDnsRepository,
    ): TracerouteUseCase = TracerouteUseCase(
        tracerouteRepository,
        geoIpRepository,
        reverseDnsRepository,
    )
}
