package com.luizeduardobrandao.appreceitascha.ui.recipes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luizeduardobrandao.appreceitascha.domain.auth.AuthRepository
import com.luizeduardobrandao.appreceitascha.domain.auth.AuthState
import com.luizeduardobrandao.appreceitascha.domain.auth.PlanState
import com.luizeduardobrandao.appreceitascha.domain.auth.PlanType
import com.luizeduardobrandao.appreceitascha.domain.auth.UserSessionState
import com.luizeduardobrandao.appreceitascha.domain.favorites.FavoritesRepository
import com.luizeduardobrandao.appreceitascha.domain.recipes.Recipe
import com.luizeduardobrandao.appreceitascha.domain.recipes.RecipeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel responsável pela tela de detalhes da receita.
 *
 * - Carrega 1 receita pelo ID.
 * - Controla estado de favorito para o usuário atual.
 * - Usa [UserSessionState] para saber se o usuário pode favoritar:
 *      • LOGADO + COM_PLANO → pode favoritar
 *      • Caso contrário → mostra aviso de que precisa de login/plano
 */
@HiltViewModel
class RecipeDetailViewModel @Inject constructor(
    private val recipeRepository: RecipeRepository,
    private val favoritesRepository: FavoritesRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    companion object {
        /**
         * Código interno para indicar especificamente a situação:
         * "Usuário sem login ou sem plano não pode usar favoritos".
         */
        const val ERROR_FAVORITE_REQUIRES_PLAN_OR_LOGIN =
            "ERROR_FAVORITE_REQUIRES_PLAN_OR_LOGIN"
    }

    private val _uiState = MutableStateFlow(
        RecipeDetailUiState(
            sessionState = UserSessionState(
                authState = AuthState.NAO_LOGADO,
                planState = PlanState.SEM_PLANO,
                planType = PlanType.NONE
            )
        )
    )
    val uiState: StateFlow<RecipeDetailUiState> = _uiState.asStateFlow()

    /**
     * Carrega os dados da receita pelo [recipeId].
     */
    fun loadRecipe(recipeId: String) {
        if (recipeId.isBlank()) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    errorMessage = null,
                    currentRecipeId = recipeId
                )
            }

            val result = recipeRepository.getRecipeById(recipeId)

            result
                .onSuccess { recipe ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            recipe = recipe
                        )
                    }
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = throwable.message
                                ?: "RECIPE_DETAIL_LOAD_ERROR"
                        )
                    }
                }
        }
    }

    /**
     * Atualiza o [UserSessionState] a partir do [AuthRepository].
     * - Enxerga NAO_LOGADO / LOGADO + SEM_PLANO / LOGADO + COM_PLANO.
     */
    fun refreshSessionState() {
        viewModelScope.launch {
            val result = authRepository.getCurrentUserSessionState()

            val session = result.getOrElse {
                UserSessionState(
                    authState = AuthState.NAO_LOGADO,
                    planState = PlanState.SEM_PLANO,
                    planType = PlanType.NONE
                )
            }

            _uiState.update { it.copy(sessionState = session) }
        }
    }

    /**
     * Sincroniza o estado de "favorito" desta receita para o usuário logado.
     * - Se não houver usuário logado → não faz nada (não é erro).
     */
    fun syncFavoriteState() {
        viewModelScope.launch {
            val currentRecipeId = _uiState.value.currentRecipeId ?: return@launch

            val user = authRepository.getCurrentUser() ?: return@launch

            val result = favoritesRepository.getFavoriteRecipeIds(user.uid)

            result
                .onSuccess { ids ->
                    _uiState.update {
                        it.copy(isFavorite = ids.contains(currentRecipeId))
                    }
                }
                .onFailure { throwable ->
                    // Erro técnico ao sincronizar favoritos
                    _uiState.update {
                        it.copy(
                            errorMessage = throwable.message ?: "FAVORITE_SYNC_ERROR"
                        )
                    }
                }
        }
    }

    /**
     * Alterna o estado de favorito:
     * - Se usuário NÃO estiver autorizado (sem login ou sem plano) → seta erro específico.
     * - Se autorizado → adiciona/remove em /favorites/{uid}.
     */
    fun toggleFavorite() {
        viewModelScope.launch {
            val currentRecipeId = _uiState.value.currentRecipeId ?: return@launch

            val session = _uiState.value.sessionState

            val isAuthorized =
                session.authState == AuthState.LOGADO &&
                        session.planState == PlanState.COM_PLANO

            if (!isAuthorized) {
                // Situação de regra de negócio → mensagem específica na UI
                _uiState.update {
                    it.copy(errorMessage = ERROR_FAVORITE_REQUIRES_PLAN_OR_LOGIN)
                }
                return@launch
            }

            val user = authRepository.getCurrentUser()
            if (user == null) {
                // Segurança extra: se por algum motivo não houver usuário, trata igual
                _uiState.update {
                    it.copy(errorMessage = ERROR_FAVORITE_REQUIRES_PLAN_OR_LOGIN)
                }
                return@launch
            }

            val isCurrentlyFavorite = _uiState.value.isFavorite

            val result = if (isCurrentlyFavorite) {
                favoritesRepository.removeFavorite(user.uid, currentRecipeId)
            } else {
                favoritesRepository.addFavorite(user.uid, currentRecipeId)
            }

            result
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            isFavorite = !isCurrentlyFavorite,
                            errorMessage = null
                        )
                    }
                }
                .onFailure { throwable ->
                    // Aqui é ERRO TÉCNICO (Firebase, rede, permissão inesperada etc.)
                    _uiState.update {
                        it.copy(
                            errorMessage = throwable.message ?: "FAVORITE_GENERIC_ERROR"
                        )
                    }
                }
        }
    }

    /**
     * Limpa o erro atual para evitar que a tela fique repetindo Snackbar.
     */
    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}

/**
 * Estado da UI para a tela de detalhes da receita.
 */
data class RecipeDetailUiState(
    val isLoading: Boolean = false,
    val currentRecipeId: String? = null,
    val recipe: Recipe? = null,
    val isFavorite: Boolean = false,
    val sessionState: UserSessionState,
    val errorMessage: String? = null
)