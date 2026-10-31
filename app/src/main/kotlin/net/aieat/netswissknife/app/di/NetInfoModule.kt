package net.aieat.netswissknife.app.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.aieat.netswissknife.app.platform.AndroidNetInfoReader
import net.aieat.netswissknife.app.platform.NetInfoLocalReader
import net.aieat.netswissknife.core.domain.PublicIpLookupUseCase
import net.aieat.netswissknife.core.network.netinfo.OkHttpPublicIpRepository
import net.aieat.netswissknife.core.network.netinfo.PublicIpRepository
import net.aieat.netswissknife.core.network.traceroute.GeoIpRepository
import net.aieat.netswissknife.core.network.traceroute.TracerouteReverseDnsRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetInfoModule {
    @Provides
    @Singleton
    fun provideNetInfoLocalReader(
        @ApplicationContext context: Context,
    ): NetInfoLocalReader = AndroidNetInfoReader(context)

    @Provides
    @Singleton
    fun providePublicIpRepository(): PublicIpRepository = OkHttpPublicIpRepository()

    /** Reverse DNS and geo/ASN reuse the bindings from [TracerouteModule]; they are not re-provided here. */
    @Provides
    @Singleton
    fun providePublicIpLookupUseCase(
        publicIp: PublicIpRepository,
        geoIp: GeoIpRepository,
        reverseDns: TracerouteReverseDnsRepository,
    ): PublicIpLookupUseCase = PublicIpLookupUseCase(publicIp, geoIp, reverseDns)
}
