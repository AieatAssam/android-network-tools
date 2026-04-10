package net.aieat.netswissknife.app.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.aieat.netswissknife.core.domain.SubnetCalculatorUseCase
import net.aieat.netswissknife.core.network.subnet.SubnetCalculatorRepository
import net.aieat.netswissknife.core.network.subnet.SubnetCalculatorRepositoryImpl
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SubnetCalculatorModule {

    @Provides
    @Singleton
    fun provideSubnetCalculatorRepository(): SubnetCalculatorRepository =
        SubnetCalculatorRepositoryImpl()

    @Provides
    @Singleton
    fun provideSubnetCalculatorUseCase(repo: SubnetCalculatorRepository): SubnetCalculatorUseCase =
        SubnetCalculatorUseCase(repo)
}
