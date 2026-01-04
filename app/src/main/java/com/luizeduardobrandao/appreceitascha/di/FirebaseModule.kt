package com.luizeduardobrandao.appreceitascha.di

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.functions.FirebaseFunctions
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Módulo responsável por fornecer instâncias do Firebase.
 *
 * Centraliza a criação de [FirebaseAuth] e [FirebaseDatabase] para garantir
 * instâncias únicas (Singleton) para toda a aplicação.
 */
@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {

    /**
     * Fornece a instância padrão do [FirebaseAuth]. Usada para login/cadastro/recuperação de senha.
     */
    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    /**
     * Fornece a instância padrão do [FirebaseDatabase].
     * Usada para salvar e ler perfis de usuário e outros dados.
     */
    @Provides
    @Singleton
    fun provideFirebaseDatabase(): FirebaseDatabase = FirebaseDatabase.getInstance()

    @Provides
    @Singleton
    fun provideFirebaseFunctions(): FirebaseFunctions = FirebaseFunctions.getInstance()
}
