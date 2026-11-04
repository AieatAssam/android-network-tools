package net.aieat.netswissknife.app.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.aieat.netswissknife.core.domain.OuiLookupUseCase
import net.aieat.netswissknife.core.network.lan.OuiDatabase
import net.aieat.netswissknife.core.network.oui.OuiRegistry

@Module
@InstallIn(SingletonComponent::class)
object OuiModule {
    /** The bundled IEEE registry, shared with the LAN Scanner and Wi-Fi Scanner vendor lookups. */
    @Provides
    fun provideOuiRegistry(): OuiRegistry = OuiDatabase

    @Provides
    fun provideOuiLookupUseCase(registry: OuiRegistry): OuiLookupUseCase = OuiLookupUseCase(registry)
}
