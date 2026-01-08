package com.luizeduardobrandao.appreceitascha.ui.favorites

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

@HiltViewModel
class FavoritesViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val favoritesRepository: FavoritesRepository,
    private val recipeRepository: RecipeRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        FavoritesUiState(
            sessionState = UserSessionState(
                authState = AuthState.NAO_LOGADO,
                planState = PlanState.SEM_PLANO,
                planType = PlanType.NONE
            )
        )
    )
    val uiState: StateFlow<FavoritesUiState> = _uiState.asStateFlow()

    init {
        loadFavorites()
    }

    /**
     * Carrega a lista de receitas favoritas do usuário.
     *
     * ✅ REGRA 4 IMPLEMENTADA:
     * - Filtra apenas receitas que o usuário pode acessar
     * - Se perder plano, receitas premium favoritadas ficam ocultas
     */
    fun loadFavorites() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    errorMessage = null
                )
            }

            val sessionResult = authRepository.getCurrentUserSessionState()
            val session = sessionResult.getOrElse {
                UserSessionState(
                    authState = AuthState.NAO_LOGADO,
                    planState = PlanState.SEM_PLANO,
                    planType = PlanType.NONE
                )
            }

            _uiState.update { it.copy(sessionState = session) }

            val user = authRepository.getCurrentUser()

            val isAuthorized =
                session.authState == AuthState.LOGADO &&
                        session.planState == PlanState.COM_PLANO &&
                        user != null

            if (!isAuthorized) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        recipes = emptyList(),
                        isFeatureAvailable = false,
                        errorMessage = null
                    )
                }
                return@launch
            }

            val favResult = user.let { favoritesRepository.getFavoriteRecipeIds(it.uid) }

            favResult.onSuccess { ids ->
                if (ids.isEmpty()) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            recipes = emptyList(),
                            isFeatureAvailable = true,
                            errorMessage = null
                        )
                    }
                    return@onSuccess
                }

                val loadedRecipes = mutableListOf<Recipe>()
                var hadError = false

                for (recipeId in ids) {
                    val recipeResult = recipeRepository.getRecipeById(recipeId)
                    recipeResult
                        .onSuccess { recipe ->
                            // ✅ CRÍTICO: Filtra apenas receitas acessíveis
                            if (recipe != null && canAccessRecipe(recipe, session)) {
                                loadedRecipes.add(recipe)
                            }
                        }
                        .onFailure {
                            hadError = true
                        }
                }

                _uiState.update { it ->
                    it.copy(
                        isLoading = false,
                        recipes = loadedRecipes.sortedBy { it.title.lowercase() },
                        isFeatureAvailable = true,
                        errorMessage = if (hadError) {
                            "FAVORITES_PARTIAL_LOAD_ERROR"
                        } else {
                            null
                        }
                    )
                }
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        recipes = emptyList(),
                        isFeatureAvailable = true,
                        errorMessage = throwable.message ?: "FAVORITES_GENERIC_ERROR"
                    )
                }
            }
        }
    }

    /**
     * ✅ CRÍTICO: Valida se o usuário pode acessar uma receita específica.
     *
     * Regras de Acesso:
     * - LOGADO + COM_PLANO → pode acessar qualquer receita (free ou premium)
     * - NAO_LOGADO ou LOGADO + SEM_PLANO → apenas receitas com isFreePreview = true
     *
     * Esta função é a implementação da REGRA 4:
     * "Receitas bloqueadas voltam a ficar bloqueadas" após perda de plano.
     */
    private fun canAccessRecipe(recipe: Recipe, session: UserSessionState): Boolean {
        return when {
            session.authState == AuthState.LOGADO &&
                    session.planState == PlanState.COM_PLANO -> true

            else -> recipe.isFreePreview
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun clearLastRemovedRecipeTitle() {
        _uiState.update { it.copy(lastRemovedRecipeTitle = null) }
    }
}

/**
 * Estado da UI da tela de Favoritos.
 */
data class FavoritesUiState(
    val isLoading: Boolean = false,
    val recipes: List<Recipe> = emptyList(),
    val isFeatureAvailable: Boolean = false,
    val sessionState: UserSessionState,
    val errorMessage: String? = null,
    val lastRemovedRecipeTitle: String? = null
)