package com.luizeduardobrandao.appreceitascha.di

import com.luizeduardobrandao.appreceitascha.data.recipes.RecipeRepositoryImpl
import com.luizeduardobrandao.appreceitascha.domain.recipes.RecipeRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RecipesModule {

    @Binds
    @Singleton
    abstract fun bindRecipeRepository(
        impl: RecipeRepositoryImpl
    ): RecipeRepository
}