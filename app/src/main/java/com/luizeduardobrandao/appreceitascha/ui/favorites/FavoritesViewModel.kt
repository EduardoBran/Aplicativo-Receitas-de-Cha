package com.luizeduardobrandao.appreceitascha.ui.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luizeduardobrandao.appreceitascha.domain.auth.AuthState
import com.luizeduardobrandao.appreceitascha.domain.auth.PlanState
import com.luizeduardobrandao.appreceitascha.domain.auth.UserSessionState
import com.luizeduardobrandao.appreceitascha.domain.favorites.FavoritesRepository
import com.luizeduardobrandao.appreceitascha.domain.recipes.Recipe
import com.luizeduardobrandao.appreceitascha.domain.recipes.RecipeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel responsável pela tela de favoritos.
 *
 * - Carrega IDs favoritos de /favorites/{uid}.
 * - Para cada ID, busca a [Recipe] correspondente em /recipes.
 * - Respeita o estado de sessão: apenas LOGADO + COM_PLANO exibe a lista.
 */
@HiltViewModel
class FavoritesViewModel @Inject constructor(
    private val favoritesRepository: FavoritesRepository,
    private val recipeRepository: RecipeRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        FavoritesUiState(
            sessionState = UserSessionState(
                authState = AuthState.NAO_LOGADO,
                planState = PlanState.SEM_PLANO
            )
        )
    )
    val uiState: StateFlow<FavoritesUiState> = _uiState.asStateFlow()

    /**
     * Atualiza o estado de sessão.
     * Deve ser chamado pelo Fragment antes de carregar os favoritos.
     */
    fun updateSessionState(sessionState: UserSessionState) {
        _uiState.update { it.copy(sessionState = sessionState) }
    }

    /**
     * Carrega as receitas favoritas do usuário [uid].
     *
     * Regras:
     * - LOGADO + COM_PLANO → carrega normalmente.
     * - NAO_LOGADO / SEM_PLANO → marca "isLockedByPlan" = true
     *   e não tenta buscar nada no Firebase.
     */
    fun loadFavorites(uid: String) {
        viewModelScope.launch {
            val session = _uiState.value.sessionState

            val isAllowed =
                session.authState == AuthState.LOGADO &&
                        session.planState == PlanState.COM_PLANO

            if (!isAllowed) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        recipes = emptyList(),
                        isLockedByPlan = true,
                        errorMessage = null
                    )
                }
                return@launch
            }

            _uiState.update {
                it.copy(
                    isLoading = true,
                    errorMessage = null,
                    isLockedByPlan = false
                )
            }

            val idsResult = favoritesRepository.getFavoriteRecipeIds(uid)

            idsResult
                .onSuccess { ids ->
                    if (ids.isEmpty()) {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                recipes = emptyList()
                            )
                        }
                        return@onSuccess
                    }

                    // Busca todas as receitas em paralelo para os IDs retornados.
                    val recipes = loadRecipesForIds(ids)

                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            recipes = recipes
                        )
                    }
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = throwable.message
                                ?: "Erro ao carregar favoritos."
                        )
                    }
                }
        }
    }

    /**
     * Remove uma receita favorita da lista e do Firebase.
     */
    fun removeFavorite(uid: String, recipeId: String) {
        viewModelScope.launch {
            val result = favoritesRepository.removeFavorite(uid, recipeId)

            result
                .onSuccess {
                    _uiState.update { state ->
                        state.copy(
                            recipes = state.recipes.filterNot { it.id == recipeId }
                        )
                    }
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            errorMessage = throwable.message
                                ?: "Erro ao remover favorito."
                        )
                    }
                }
        }
    }

    /**
     * Limpa mensagem de erro.
     */
    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    /**
     * Função auxiliar para buscar todas as receitas de uma lista de IDs.
     *
     * OBS: hoje usa [RecipeRepository.getRecipeById] para cada ID.
     *      Se necessário otimizar no futuro, pode ser criado no domínio
     *      uma função como `getRecipesByIds(ids: List<String>)`.
     */
    private suspend fun loadRecipesForIds(ids: List<String>): List<Recipe> =
        coroutineScope {
            ids.map { id ->
                async {
                    val result = recipeRepository.getRecipeById(id)
                    result.getOrNull()
                }
            }
                .awaitAll()
                .filterNotNull()
        }
}

/**
 * Estado da UI para a tela de favoritos.
 */
data class FavoritesUiState(
    val isLoading: Boolean = false,
    val recipes: List<Recipe> = emptyList(),
    val sessionState: UserSessionState,
    val isLockedByPlan: Boolean = false,
    val errorMessage: String? = null
)