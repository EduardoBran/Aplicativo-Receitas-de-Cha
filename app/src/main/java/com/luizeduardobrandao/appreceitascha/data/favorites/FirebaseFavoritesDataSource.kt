package com.luizeduardobrandao.appreceitascha.data.favorites

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Data source de acesso cru ao nó /favorites do Realtime Database.
 *
 * Estrutura:
 *   /favorites/{uid}/{recipeId} = true
 *
 * Aqui NÃO há lógica de regra de negócio, apenas leitura/escrita bruta.
 * A camada de domínio (FavoritesRepositoryImpl) converte isso em listas de ids.
 */
@Singleton
class FirebaseFavoritesDataSource @Inject constructor(
    firebaseDatabase: FirebaseDatabase
) {

    private val favoritesRef = firebaseDatabase.getReference("favorites")

    /**
     * Lê todos os favoritos de um usuário em /favorites/{uid}.
     */
    suspend fun getFavoritesSnapshot(uid: String): DataSnapshot {
        return favoritesRef
            .child(uid)
            .get()
            .await()
    }

    /**
     * Marca uma receita como favorita em /favorites/{uid}/{recipeId} = true.
     */
    suspend fun addFavorite(uid: String, recipeId: String) {
        favoritesRef
            .child(uid)
            .child(recipeId)
            .setValue(true)
            .await()
    }

    /**
     * Remove uma receita favorita em /favorites/{uid}/{recipeId}.
     */
    suspend fun removeFavorite(uid: String, recipeId: String) {
        favoritesRef
            .child(uid)
            .child(recipeId)
            .removeValue()
            .await()
    }
}