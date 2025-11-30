package com.luizeduardobrandao.appreceitascha.di

import com.luizeduardobrandao.appreceitascha.data.favorites.FavoritesRepositoryImpl
import com.luizeduardobrandao.appreceitascha.domain.favorites.FavoritesRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class FavoritesModule {

    @Binds
    @Singleton
    abstract fun bindFavoritesRepository(
        impl: FavoritesRepositoryImpl
    ): FavoritesRepository
}