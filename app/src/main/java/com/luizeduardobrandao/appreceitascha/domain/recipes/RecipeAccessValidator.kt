package com.luizeduardobrandao.appreceitascha.domain.recipes

import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Validador de acesso a receitas com verificação server-side.
 *
 * Responsável por:
 * - Validar se o usuário tem permissão para acessar uma receita
 * - Chamar Cloud Function que verifica plano e isFreePreview
 * - Retornar receita completa apenas se autorizado
 *
 * Segurança:
 * - Validação server-side (impossível burlar)
 * - Não expõe dados premium para usuários sem plano
 */
@Singleton
class RecipeAccessValidator @Inject constructor(
    private val functions: FirebaseFunctions
) {

    /**
     * Valida acesso à receita no servidor e retorna os dados.
     *
     * @param recipeId ID da receita a ser validada
     * @return Result com Recipe se autorizado, null se sem permissão, ou erro
     */
    suspend fun validateAndGetRecipe(recipeId: String): Result<Recipe?> {
        return try {
            val data = hashMapOf("recipeId" to recipeId)

            val result = functions
                .getHttpsCallable("validateRecipeAccess")
                .call(data)
                .await()

            val resultData = result.data as? Map<*, *>
            val hasAccess = resultData?.get("hasAccess") as? Boolean ?: false

            if (!hasAccess) {
                // Usuário não tem permissão para acessar esta receita
                return Result.success(null)
            }

            val recipeData = resultData["recipe"] as? Map<*, *>
            val recipe = recipeData?.let { mapToRecipe(it) }

            Result.success(recipe)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Converte Map retornado da Cloud Function para objeto Recipe.
     */
    private fun mapToRecipe(data: Map<*, *>): Recipe? {
        return try {
            Recipe(
                id = data["id"] as? String ?: return null,
                categoryId = data["categoryId"] as? String ?: return null,
                title = data["title"] as? String ?: return null,
                subtitle = data["subtitle"] as? String ?: return null,
                shortDescription = data["shortDescription"] as? String ?: return null,
                ingredientes = data["ingredientes"] as? String ?: return null,
                modoDePreparo = data["modoDePreparo"] as? String ?: return null,
                beneficios = data["beneficios"] as? String ?: return null,
                observacoes = data["observacoes"] as? String ?: return null,
                isFreePreview = data["isFreePreview"] as? Boolean ?: false
            )
        } catch (e: Exception) {
            null
        }
    }
}