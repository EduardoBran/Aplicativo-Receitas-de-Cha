package com.luizeduardobrandao.appreceitascha.di

import android.app.Application
import com.google.firebase.functions.FirebaseFunctions
import com.luizeduardobrandao.appreceitascha.data.billing.BillingDataSource
import com.luizeduardobrandao.appreceitascha.data.billing.BillingRepositoryImpl
import com.luizeduardobrandao.appreceitascha.domain.auth.AuthRepository
import com.luizeduardobrandao.appreceitascha.domain.billing.BillingRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object BillingModule {

    @Provides
    @Singleton
    fun provideBillingDataSource(application: Application): BillingDataSource {
        return BillingDataSource(application)
    }

    @Provides
    @Singleton
    fun provideBillingRepository(
        billingDataSource: BillingDataSource,
        authRepository: AuthRepository,
        firebaseFunctions: FirebaseFunctions
    ): BillingRepository {
        return BillingRepositoryImpl(
            billingDataSource = billingDataSource,
            authRepository = authRepository,
            firebaseFunctions = firebaseFunctions
        )
    }
}