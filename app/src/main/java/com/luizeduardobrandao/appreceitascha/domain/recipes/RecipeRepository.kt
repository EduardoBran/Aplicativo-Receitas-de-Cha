package com.luizeduardobrandao.appreceitascha.domain.recipes

/**
 * Contrato da camada de domínio para acesso a categorias e receitas.
 *
 * Responsabilidades principais:
 * - Esconder detalhes de implementação da camada de dados
 *   (Firebase Realtime Database, caches, DTOs, etc.).
 * - Entregar modelos de domínio [Category] e [Recipe] prontos para uso na UI.
 * - Centralizar a lógica de leitura de:
 *      /categories
 *      /recipes
 *
 * Padrão de retorno:
 * - Usa [Result] para seguir o mesmo padrão do "AuthRepository":
 *      - onSuccess → dados prontos para uso.
 *      - onFailure → ViewModel/Fragment decide como exibir o erro (Snackbar, etc.).
 *
 * Implementação concreta:
 * - Será fornecida na camada data em:
 *      com.luizeduardobrandao.appreceitascha.data.recipes.RecipeRepositoryImpl
 *   que, por sua vez, utilizará um data source do Firebase.
 */
interface RecipeRepository {

    /**
     * Busca todas as categorias disponíveis, já ordenadas conforme o campo [Category.order].
     *
     * Usado em:
     * - CategoriesViewModel / CategoriesFragment (lista de categorias).
     * - Fluxo de navegação a partir de HomeFragment → "Ver receitas".
     *
     * Regra de negócio:
     * - Deve refletir exatamente as 8 categorias mapeadas em /categories.
     */
    suspend fun getCategories(): Result<List<Category>>

    /**
     * Busca todas as receitas associadas a uma categoria específica.
     *
     * Parâmetros:
     * - [categoryId] → id da categoria (ex.: "calmante", "digestivo"...).
     *
     * Usado em:
     * - RecipeListViewModel / RecipeListFragment:
     *      → para exibir todas as receitas da categoria, inclusive premium,
     *        usando [Recipe.isFreePreview] + estado do usuário para bloquear ou liberar o clique.
     */
    suspend fun getRecipesByCategory(categoryId: String): Result<List<Recipe>>

    /**
     * Busca uma receita específica pelo seu [recipeId].
     *
     * Parâmetros:
     * - [recipeId] → id da receita (ex.: "r1", "r2"...).
     *
     * Usado em:
     * - RecipeDetailViewModel / RecipeDetailFragment:
     *      → carregar detalhes de uma receita a partir de uma navegação que traz só o id.
     * - FavoritesViewModel:
     *      → montar a lista de receitas favoritedas a partir dos ids em /favorites/{uid}.
     *
     * Retorno:
     * - [Result.success] com:
     *      - a instância de [Recipe], se encontrada;
     *      - ou null, se o id não existir.
     * - [Result.failure] em caso de erro de rede, permissão, parsing, etc.
     */
    suspend fun getRecipeById(recipeId: String): Result<Recipe?>
}