package com.luizeduardobrandao.appreceitascha.di

import com.google.firebase.functions.FirebaseFunctions
import com.luizeduardobrandao.appreceitascha.data.recipes.RecipeRepositoryImpl
import com.luizeduardobrandao.appreceitascha.domain.recipes.RecipeAccessValidator
import com.luizeduardobrandao.appreceitascha.domain.recipes.RecipeRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Módulo Hilt para injeção de dependências relacionadas a receitas.
 *
 * Fornece:
 * - RecipeRepository (acesso a dados)
 * - RecipeAccessValidator (validação server-side)
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RecipesModule {

    /**
     * Vincula a implementação do repositório de receitas.
     */
    @Binds
    @Singleton
    abstract fun bindRecipeRepository(
        impl: RecipeRepositoryImpl
    ): RecipeRepository

    companion object {
        /**
         * Fornece o validador de acesso a receitas via Cloud Function.
         * Usado para validação server-side de permissões.
         */
        @Provides
        @Singleton
        fun provideRecipeAccessValidator(
            firebaseFunctions: FirebaseFunctions
        ): RecipeAccessValidator {
            return RecipeAccessValidator(firebaseFunctions)
        }
    }
}