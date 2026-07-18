package net.aieat.netswissknife.app.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.aieat.netswissknife.core.domain.WakeOnLanUseCase
import net.aieat.netswissknife.core.network.wol.WakeOnLanRepository
import net.aieat.netswissknife.core.network.wol.WakeOnLanRepositoryImpl
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object WakeOnLanModule {

    @Provides
    @Singleton
    fun provideWakeOnLanRepository(): WakeOnLanRepository = WakeOnLanRepositoryImpl()

    @Provides
    @Singleton
    fun provideWakeOnLanUseCase(repo: WakeOnLanRepository): WakeOnLanUseCase =
        WakeOnLanUseCase(repo)
}
