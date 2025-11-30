package com.luizeduardobrandao.appreceitascha.data.favorites

import com.luizeduardobrandao.appreceitascha.domain.favorites.FavoritesRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementação de [FavoritesRepository] usando [FirebaseFavoritesDataSource].
 *
 * Converte:
 *   /favorites/{uid}/{recipeId} = true
 * em:
 *   List<String> de recipeId.
 *
 * Não faz verificação de estado do usuário (NAO_LOGADO / SEM_PLANO / COM_PLANO);
 * isso é responsabilidade da camada de UI/UseCases usando UserSessionState/PlanState.
 */
@Singleton
class FavoritesRepositoryImpl @Inject constructor(
    private val firebaseFavoritesDataSource: FirebaseFavoritesDataSource
) : FavoritesRepository {

    override suspend fun getFavoriteRecipeIds(uid: String): Result<List<String>> {
        return try {
            val snapshot = firebaseFavoritesDataSource.getFavoritesSnapshot(uid)

            val ids = snapshot.children
                .mapNotNull { it.key }
                .toList()

            Result.success(ids)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun addFavorite(uid: String, recipeId: String): Result<Unit> {
        return try {
            firebaseFavoritesDataSource.addFavorite(uid, recipeId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun removeFavorite(uid: String, recipeId: String): Result<Unit> {
        return try {
            firebaseFavoritesDataSource.removeFavorite(uid, recipeId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}