package com.luizeduardobrandao.appreceitascha.data.recipes

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Data source de acesso cru ao Firebase Realtime Database para categorias e receitas.
 *
 * Responsável apenas por:
 * - Apontar para os nós corretos (/categories e /recipes).
 * - Retornar [DataSnapshot] crus, sem converter para modelos de domínio.
 *
 * Conversão para "Category" / "Recipe" é responsabilidade do [RecipeRepositoryImpl].
 */
@Singleton
class FirebaseRecipeDataSource @Inject constructor(
    firebaseDatabase: FirebaseDatabase
) {

    private val categoriesRef = firebaseDatabase.getReference("categories")
    private val recipesRef = firebaseDatabase.getReference("recipes")

    /**
     * Retorna o snapshot bruto de /categories.
     *
     * Usado por [RecipeRepositoryImpl] em:
     * - getCategories()
     */
    suspend fun getCategoriesSnapshot(): DataSnapshot {
        return categoriesRef.get().await()
    }

    /**
     * Retorna o snapshot bruto de /recipes filtrando por [categoryId].
     *
     * Usado por [RecipeRepositoryImpl] em:
     * - getRecipesByCategory(categoryId)
     */
    suspend fun getRecipesByCategorySnapshot(categoryId: String): DataSnapshot {
        return recipesRef
            .orderByChild("categoryId")
            .equalTo(categoryId)
            .get()
            .await()
    }

    /**
     * Retorna o snapshot bruto de uma receita específica em /recipes/{recipeId}.
     *
     * Usado por [RecipeRepositoryImpl] em:
     * - getRecipeById(recipeId)
     */
    suspend fun getRecipeByIdSnapshot(recipeId: String): DataSnapshot {
        return recipesRef
            .child(recipeId)
            .get()
            .await()
    }

    /**
     * Retorna o snapshot bruto de TODAS as receitas em /recipes.
     *
     * Usado por [RecipeRepositoryImpl] em:
     * - searchRecipes(query) -> Para filtrar client-side.
     */
    suspend fun getAllRecipesSnapshot(): DataSnapshot {
        return recipesRef.get().await()
    }
}