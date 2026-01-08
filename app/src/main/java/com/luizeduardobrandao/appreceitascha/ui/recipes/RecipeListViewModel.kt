package com.luizeduardobrandao.appreceitascha.ui.recipes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luizeduardobrandao.appreceitascha.domain.auth.AuthState
import com.luizeduardobrandao.appreceitascha.domain.auth.PlanState
import com.luizeduardobrandao.appreceitascha.domain.auth.UserSessionState
import com.luizeduardobrandao.appreceitascha.domain.recipes.Recipe
import com.luizeduardobrandao.appreceitascha.domain.recipes.RecipeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel responsável pela lista de receitas de uma categoria.
 *
 * - Carrega receitas via [RecipeRepository.getRecipesByCategory].
 * - Mantém o [UserSessionState] para a lógica de bloqueio/liberação de clique.
 * - A regra de quem pode abrir o quê é feita pela função [canOpenRecipe].
 */
@HiltViewModel
class RecipeListViewModel @Inject constructor(
    private val recipeRepository: RecipeRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        RecipeListUiState(
            sessionState = UserSessionState(
                authState = AuthState.NAO_LOGADO,
                planState = PlanState.SEM_PLANO
            )
        )
    )
    val uiState: StateFlow<RecipeListUiState> = _uiState.asStateFlow()

    /**
     * Atualiza o estado de sessão (NAO_LOGADO / LOGADO + SEM_PLANO / LOGADO + COM_PLANO).
     * Deve ser chamado pelo Fragment, usando o estado vindo de outro ViewModel (ex.: Auth/Main).
     */
    fun updateSessionState(sessionState: UserSessionState) {
        _uiState.update { it.copy(sessionState = sessionState) }
    }

    /**
     * Carrega todas as receitas de uma categoria específica.
     *
     * - Não filtra receitas premium; a UI usa "Recipe.isFreePreview" + "sessionState"
     *   para decidir se o usuário pode abrir ou não.
     */
    fun loadRecipes(categoryId: String) {
        if (categoryId.isBlank()) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    errorMessage = null,
                    currentCategoryId = categoryId
                )
            }

            val result = recipeRepository.getRecipesByCategory(categoryId)

            result
                .onSuccess { recipes ->
                    _uiState.update { it ->
                        it.copy(
                            isLoading = false,
                            recipes = recipes.sortedBy { it.title.lowercase() },
                            errorMessage = null
                        )
                    }
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = throwable.message
                                ?: "Erro ao carregar receitas da categoria."
                        )
                    }
                }
        }
    }

    /**
     * Regra de negócio de acesso:
     * - LOGADO + COM_PLANO → pode abrir qualquer receita.
     * - NAO_LOGADO ou LOGADO + SEM_PLANO → apenas receitas com [Recipe.isFreePreview] = true.
     */
    fun canOpenRecipe(recipe: Recipe): Boolean {
        val session = _uiState.value.sessionState

        return when {
            session.authState == AuthState.LOGADO &&
                    session.planState == PlanState.COM_PLANO -> true

            else -> recipe.isFreePreview
        }
    }

    /**
     * Limpa a mensagem de erro atual.
     */
    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}

/**
 * Estado da UI para a lista de receitas de uma categoria.
 */
data class RecipeListUiState(
    val isLoading: Boolean = false,
    val currentCategoryId: String? = null,
    val recipes: List<Recipe> = emptyList(),
    val sessionState: UserSessionState,
    val errorMessage: String? = null
)