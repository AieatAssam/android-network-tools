package net.aieat.netswissknife.app.di

import android.content.Context
import android.net.ConnectivityManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.aieat.netswissknife.app.platform.AndroidNetworkBinder
import net.aieat.netswissknife.app.platform.ConnectivityObserver
import net.aieat.netswissknife.core.network.net.NetworkBinder
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun provideConnectivityManager(@ApplicationContext context: Context): ConnectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    @Provides
    @Singleton
    fun provideNetworkBinder(connectivityManager: ConnectivityManager): NetworkBinder =
        AndroidNetworkBinder(connectivityManager)

    @Provides
    @Singleton
    fun provideConnectivityObserver(connectivityManager: ConnectivityManager): ConnectivityObserver =
        ConnectivityObserver(connectivityManager)
}
