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
 * ViewModel responsável pelos detalhes de uma receita.
 *
 * Responsabilidades:
 * - Carrega receita completa por ID
 * - Gerencia estado de favorito
 * - Controla permissões de favoritar (requer login + plano)
 * - Sincroniza estado de favorito com Firebase
 */
@HiltViewModel
class RecipeDetailViewModel @Inject constructor(
    private val recipeRepository: RecipeRepository,
    private val authRepository: AuthRepository,
    private val favoritesRepository: FavoritesRepository
) : ViewModel() {

    companion object {
        const val ERROR_FAVORITE_REQUIRES_PLAN_OR_LOGIN =
            "ERROR_FAVORITE_REQUIRES_PLAN_OR_LOGIN"
        private const val ERROR_RECIPE_NOT_FOUND = "RECIPE_NOT_FOUND"
        private const val ERROR_GENERIC = "RECIPE_DETAIL_GENERIC_ERROR"
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
     * Carrega receita pelo ID.
     *
     * ✅ SEM VALIDAÇÃO DE ACESSO:
     * - RecipeListFragment já validou antes de navegar
     * - FavoritesFragment já validou antes de navegar
     * - Validação em 3 lugares é redundante e causa problemas de timing
     *
     * @param recipeId ID da receita a carregar
     */
    fun loadRecipe(recipeId: String) {
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
                    if (recipe == null) {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = ERROR_RECIPE_NOT_FOUND
                            )
                        }
                        return@onSuccess
                    }

                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            recipe = recipe
                        )
                    }
                }
                .onFailure { _ ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = ERROR_GENERIC
                        )
                    }
                }
        }
    }

    /**
     * Atualiza estado de sessão com base no AuthRepository (auth + plano).
     */
    fun refreshSessionState() {
        viewModelScope.launch {
            val sessionResult = authRepository.getCurrentUserSessionState()
            val session = sessionResult.getOrElse {
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
     * Sincroniza estado de favorito da receita atual com /favorites/{uid}.
     */
    fun syncFavoriteState() {
        viewModelScope.launch {
            val user = authRepository.getCurrentUser()
            val recipeId = _uiState.value.currentRecipeId

            if (user == null || recipeId == null) {
                _uiState.update { it.copy(isFavorite = false) }
                return@launch
            }

            val favResult = favoritesRepository.getFavoriteRecipeIds(user.uid)

            favResult
                .onSuccess { ids ->
                    val isFav = ids.contains(recipeId)
                    _uiState.update { it.copy(isFavorite = isFav) }
                }
                .onFailure {
                    // Erro de rede: não marca favorito e não quebra a tela
                }
        }
    }

    /**
     * Alterna estado de favorito da receita atual.
     *
     * Validações:
     * - Requer usuário logado
     * - Requer plano ativo (COM_PLANO)
     *
     * Se autorizado:
     * - Adiciona/remove de /favorites/{uid}/{recipeId}
     * - Emite lastFavoriteAction para Snackbar de sucesso
     */
    fun toggleFavorite() {
        viewModelScope.launch {
            val state = _uiState.value
            val session = state.sessionState
            val recipe = state.recipe
            val recipeId = state.currentRecipeId

            if (recipeId == null || recipe == null) {
                return@launch
            }

            val user = authRepository.getCurrentUser()
            val isAuthorized =
                user != null &&
                        session.authState == AuthState.LOGADO &&
                        session.planState == PlanState.COM_PLANO

            if (!isAuthorized) {
                _uiState.update {
                    it.copy(errorMessage = ERROR_FAVORITE_REQUIRES_PLAN_OR_LOGIN)
                }
                return@launch
            }

            if (state.isFavorite) {
                // Remover dos favoritos
                val result = favoritesRepository.removeFavorite(user.uid, recipeId)
                result
                    .onSuccess {
                        _uiState.update {
                            it.copy(
                                isFavorite = false,
                                lastFavoriteAction = RecipeFavoriteAction.REMOVED
                            )
                        }
                    }
                    .onFailure { _ ->
                        _uiState.update {
                            it.copy(errorMessage = ERROR_GENERIC)
                        }
                    }
            } else {
                // Adicionar aos favoritos
                val result = favoritesRepository.addFavorite(user.uid, recipeId)
                result
                    .onSuccess {
                        _uiState.update {
                            it.copy(
                                isFavorite = true,
                                lastFavoriteAction = RecipeFavoriteAction.ADDED
                            )
                        }
                    }
                    .onFailure { _ ->
                        _uiState.update {
                            it.copy(errorMessage = ERROR_GENERIC)
                        }
                    }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun clearFavoriteAction() {
        _uiState.update { it.copy(lastFavoriteAction = null) }
    }
}

/**
 * Ação de favorito para exibir Snackbar de sucesso na UI.
 */
enum class RecipeFavoriteAction {
    ADDED,
    REMOVED
}

/**
 * Estado da UI de detalhes da receita.
 *
 * @property isLoading Indica carregamento em andamento
 * @property currentRecipeId ID da receita atual
 * @property recipe Dados completos da receita
 * @property isFavorite Se a receita está nos favoritos do usuário
 * @property sessionState Estado de autenticação e plano
 * @property errorMessage Código de erro técnico (ou null)
 * @property lastFavoriteAction Última ação de favorito (para Snackbar)
 */
data class RecipeDetailUiState(
    val isLoading: Boolean = false,
    val currentRecipeId: String? = null,
    val recipe: Recipe? = null,
    val isFavorite: Boolean = false,
    val sessionState: UserSessionState,
    val errorMessage: String? = null,
    val lastFavoriteAction: RecipeFavoriteAction? = null
)