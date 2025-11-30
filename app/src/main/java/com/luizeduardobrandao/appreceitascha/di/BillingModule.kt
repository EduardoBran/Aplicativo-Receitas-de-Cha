package com.luizeduardobrandao.appreceitascha.di

import android.app.Application
import com.luizeduardobrandao.appreceitascha.data.billing.BillingDataSource
import com.luizeduardobrandao.appreceitascha.data.billing.BillingRepositoryImpl
import com.luizeduardobrandao.appreceitascha.domain.auth.AuthRepository
import com.luizeduardobrandao.appreceitascha.domain.billing.BillingRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Módulo Hilt responsável por prover:
 * - BillingDataSource (acesso cru ao BillingClient).
 * - BillingRepository (regra de negócio de Billing).
 */
@Module
@InstallIn(SingletonComponent::class)
object BillingModule {

    @Provides
    @Singleton
    fun provideBillingDataSource(app: Application): BillingDataSource =
        BillingDataSource(app)

    @Provides
    @Singleton
    fun provideBillingRepository(
        dataSource: BillingDataSource,
        authRepository: AuthRepository
    ): BillingRepository = BillingRepositoryImpl(dataSource, authRepository)
}