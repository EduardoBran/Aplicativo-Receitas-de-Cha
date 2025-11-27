package com.luizeduardobrandao.appreceitascha.di

import com.luizeduardobrandao.appreceitascha.data.auth.AuthRepositoryImpl
import com.luizeduardobrandao.appreceitascha.domain.auth.AuthRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Módulo de bindings da camada de domínio.
 *
 * Realiza o vínculo entre a interface [AuthRepository] e a implementação
 * concreta [AuthRepositoryImpl] para que o Hilt possa injetar o repositório
 * em ViewModels e outras classes.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    /**
     * Vincula a implementação concreta [AuthRepositoryImpl] à interface [AuthRepository].
     * Escopo Singleton para a autenticação ser compartilhada na aplicação inteira.
     */
    @Binds
    @Singleton
    abstract fun bindAuthRepository(
        impl: AuthRepositoryImpl
    ): AuthRepository
}