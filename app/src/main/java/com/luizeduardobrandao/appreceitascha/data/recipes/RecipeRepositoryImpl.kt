package com.luizeduardobrandao.appreceitascha.data.recipes

import com.google.firebase.database.DataSnapshot
import com.luizeduardobrandao.appreceitascha.domain.recipes.Category
import com.luizeduardobrandao.appreceitascha.domain.recipes.Recipe
import com.luizeduardobrandao.appreceitascha.domain.recipes.RecipeRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementação de [RecipeRepository] usando [FirebaseRecipeDataSource].
 *
 * Aqui acontece:
 * - Leitura bruta do Firebase via dataSource.
 * - Conversão de [DataSnapshot] para [Category] e [Recipe].
 * - Ordenação de listas conforme regras de negócio.
 * - Encapsulamento de exceções em [Result].
 */
@Singleton
class RecipeRepositoryImpl @Inject constructor(
    private val firebaseRecipeDataSource: FirebaseRecipeDataSource
) : RecipeRepository {

    override suspend fun getCategories(): Result<List<Category>> {
        return try {
            val snapshot = firebaseRecipeDataSource.getCategoriesSnapshot()

            val categories = snapshot.children
                .mapNotNull { child ->
                    val id = child.child("id").getValue(String::class.java) ?: child.key
                    val name = child.child("name").getValue(String::class.java)
                    val description = child.child("description").getValue(String::class.java)
                    val order = child.child("order").getValue(Long::class.java)?.toInt()

                    if (id.isNullOrBlank() ||
                        name.isNullOrBlank() ||
                        description.isNullOrBlank() ||
                        order == null
                    ) {
                        null // ignora categoria mal formatada
                    } else {
                        Category(
                            id = id,
                            name = name,
                            description = description,
                            order = order
                        )
                    }
                }
                .sortedBy { it.order }

            Result.success(categories)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getRecipesByCategory(categoryId: String): Result<List<Recipe>> {
        return try {
            val snapshot = firebaseRecipeDataSource.getRecipesByCategorySnapshot(categoryId)
            val recipes = snapshot.children.mapNotNull { it.toRecipeDomain() }
            Result.success(recipes)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getRecipeById(recipeId: String): Result<Recipe?> {
        return try {
            val snapshot = firebaseRecipeDataSource.getRecipeByIdSnapshot(recipeId)
            val recipe = if (!snapshot.exists()) null else snapshot.toRecipeDomain()
            Result.success(recipe)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }


    /**
     * Converte um [DataSnapshot] de /recipes/{id} para [Recipe],
     * validando campos obrigatórios e ignorando receitas mal formatadas.
     */
    private fun DataSnapshot.toRecipeDomain(): Recipe? {
        val idFromField = child("id").getValue(String::class.java)
        val id = if (!idFromField.isNullOrBlank()) idFromField else key

        val categoryId = child("categoryId").getValue(String::class.java)
        val title = child("title").getValue(String::class.java)
        val subtitle = child("subtitle").getValue(String::class.java)
        val shortDescription = child("shortDescription").getValue(String::class.java)
        val ingredientes = child("ingredientes").getValue(String::class.java)
        val modoDePreparo = child("modoDePreparo").getValue(String::class.java)
        val beneficios = child("beneficios").getValue(String::class.java)
        val observacoes = child("observacoes").getValue(String::class.java)
        val isFreePreview = child("isFreePreview").getValue(Boolean::class.java) ?: false

        return if (
            id.isNullOrBlank() ||
            categoryId.isNullOrBlank() ||
            title.isNullOrBlank() ||
            subtitle.isNullOrBlank() ||
            shortDescription.isNullOrBlank() ||
            ingredientes.isNullOrBlank() ||
            modoDePreparo.isNullOrBlank() ||
            beneficios.isNullOrBlank() ||
            observacoes.isNullOrBlank()
        ) {
            null
        } else {
            Recipe(
                id = id,
                categoryId = categoryId,
                title = title,
                subtitle = subtitle,
                shortDescription = shortDescription,
                ingredientes = ingredientes,
                modoDePreparo = modoDePreparo,
                beneficios = beneficios,
                observacoes = observacoes,
                isFreePreview = isFreePreview
            )
        }
    }

    // ✅ IMPLEMENTAÇÃO DA NOVA BUSCA
    override suspend fun searchRecipes(query: String): Result<List<Recipe>> {
        return try {
            // 1. Busca todas as receitas do DataSource
            val snapshot = firebaseRecipeDataSource.getAllRecipesSnapshot()

            // 2. Converte Snapshot -> List<Recipe> usando a função auxiliar
            val allRecipes = snapshot.children.mapNotNull { child ->
                child.toDomainRecipe()
            }

            // 3. Filtra localmente pelo título (ignora maiúsculas/minúsculas)
            val filteredList = allRecipes.filter { recipe ->
                recipe.title.contains(query, ignoreCase = true)
            }

            Result.success(filteredList)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // =========================================================================
    // ✅ FUNÇÃO AUXILIAR PARA BUSCA (Adicione no final da classe)
    // =========================================================================
    /**
     * Converte um DataSnapshot (nó de receita) para o objeto de domínio [Recipe].
     * Retorna null se faltar campos obrigatórios.
     */
    private fun DataSnapshot.toDomainRecipe(): Recipe? {
        // Tenta pegar o ID do campo "id", se não tiver, usa a chave do nó (key)
        val idFromField = child("id").getValue(String::class.java)
        val id = if (!idFromField.isNullOrBlank()) idFromField else key

        val categoryId = child("categoryId").getValue(String::class.java)
        val title = child("title").getValue(String::class.java)
        val subtitle = child("subtitle").getValue(String::class.java)
        val shortDescription = child("shortDescription").getValue(String::class.java)
        val ingredientes = child("ingredientes").getValue(String::class.java)
        val modoDePreparo = child("modoDePreparo").getValue(String::class.java)
        val beneficios = child("beneficios").getValue(String::class.java)
        val observacoes = child("observacoes").getValue(String::class.java)

        // Se isFreePreview não existir no banco, assume false (bloqueado)
        val isFreePreview = child("isFreePreview").getValue(Boolean::class.java) ?: false

        // Validação de campos obrigatórios
        return if (
            id.isNullOrBlank() ||
            categoryId.isNullOrBlank() ||
            title.isNullOrBlank() ||
            subtitle.isNullOrBlank() ||
            shortDescription.isNullOrBlank() ||
            ingredientes.isNullOrBlank() ||
            modoDePreparo.isNullOrBlank() ||
            beneficios.isNullOrBlank() ||
            observacoes.isNullOrBlank()
        ) {
            null
        } else {
            Recipe(
                id = id,
                categoryId = categoryId,
                title = title,
                subtitle = subtitle,
                shortDescription = shortDescription,
                ingredientes = ingredientes,
                modoDePreparo = modoDePreparo,
                beneficios = beneficios,
                observacoes = observacoes,
                isFreePreview = isFreePreview
            )
        }
    }
}