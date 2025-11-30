package com.luizeduardobrandao.appreceitascha.domain.favorites

/**
 * Contrato da camada de domínio para gerenciamento de favoritos.
 *
 * Estrutura no Realtime Database:
 *   /favorites/{uid}/{recipeId} = true
 *
 * Regras de negócio associadas:
 * - Apenas usuários LOGADO + COM_PLANO podem ter favoritos funcionais.
 * - Na UI:
 *      - RecipeDetailFragment:
 *          → ícone de favorito só ativa funções deste repositório se o usuário
 *            estiver COM_PLANO (regra definida na camada de UI/UseCase).
 *      - FavoritesFragment:
 *          → lista só é exibida para LOGADO + COM_PLANO.
 *
 * Esta interface não decide se o usuário pode ou não favoritar;
 * ela apenas oferece operações de leitura/escrita no nó de favoritos.
 * A validação de estado (NAO_LOGADO / SEM_PLANO / COM_PLANO) é feita pelos
 * ViewModels usando UserSessionState/PlanState.
 */
interface FavoritesRepository {

    /**
     * Obtém a lista de ids de receitas favoritedas por um usuário.
     *
     * Parâmetros:
     * - [uid] → identificador único do usuário (FirebaseAuth.uid).
     *
     * Retorno:
     * - [Result.success] com uma lista de recipeId:
     *      → ex.: ["r1", "r5", "r10"].
     * - [Result.failure] em caso de erro de rede, permissão, etc.
     *
     * Uso:
     * - FavoritesViewModel:
     *      → combinar os recipeId retornados com RecipeRepository.getRecipeById(...)
     *        ou com um mét0do otimizado para carregar múltiplas receitas.
     */
    suspend fun getFavoriteRecipeIds(uid: String): Result<List<String>>

    /**
     * Adiciona uma receita aos favoritos do usuário.
     *
     * Parâmetros:
     * - [uid]      → identificador único do usuário.
     * - [recipeId] → id da receita a ser favoritada.
     *
     * Retorno:
     * - [Result.success(Unit)] em caso de sucesso.
     * - [Result.failure] em caso de erro.
     *
     * Uso:
     * - RecipeDetailViewModel / RecipeDetailFragment:
     *      → quando o usuário LOGADO + COM_PLANO tocar no ícone de favorito
     *        e a receita ainda não estiver favoritada.
     */
    suspend fun addFavorite(uid: String, recipeId: String): Result<Unit>

    /**
     * Remove uma receita dos favoritos do usuário.
     *
     * Parâmetros:
     * - [uid]      → identificador único do usuário.
     * - [recipeId] → id da receita a ser removida dos favoritos.
     *
     * Retorno:
     * - [Result.success(Unit)] em caso de sucesso.
     * - [Result.failure] em caso de erro.
     *
     * Uso:
     * - RecipeDetailViewModel / RecipeDetailFragment:
     *      → quando o usuário LOGADO + COM_PLANO tocar no ícone de favorito
     *        e a receita já estiver salva.
     */
    suspend fun removeFavorite(uid: String, recipeId: String): Result<Unit>
}