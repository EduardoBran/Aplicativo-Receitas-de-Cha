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
import java.text.Collator
import java.util.Locale

/**
 * ViewModel responsável pela lista de receitas de uma categoria.
 *
 * Responsabilidades:
 * - Carrega receitas via [RecipeRepository.getRecipesByCategory]
 * - Ordena receitas alfabeticamente (locale-aware)
 * - Mantém [UserSessionState] para lógica de acesso
 * - Determina se usuário pode abrir cada receita via [canOpenRecipe]
 */
@HiltViewModel
class RecipeListViewModel @Inject constructor(
    private val recipeRepository: RecipeRepository
) : ViewModel() {

    companion object {
        private const val ERROR_LOAD_RECIPES = "RECIPES_LIST_LOAD_FAILED"
    }

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
     * Atualiza estado de sessão (NAO_LOGADO / LOGADO + SEM_PLANO / COM_PLANO).
     * Deve ser chamado pelo Fragment usando estado do MainViewModel.
     */
    fun updateSessionState(sessionState: UserSessionState) {
        _uiState.update { it.copy(sessionState = sessionState) }
    }

    /**
     * Carrega receitas de uma categoria específica.
     *
     * Não filtra receitas premium - a UI usa {Recipe.isFreePreview} + {sessionState}
     * para decidir acesso.
     *
     * @param categoryId ID da categoria para filtrar receitas
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
                    val collator = Collator.getInstance(Locale.getDefault()).apply {
                        // Ignora maiúsc/minúsc, considera acentos (bom para PT-BR)
                        strength = Collator.SECONDARY
                    }

                    val sorted = recipes.sortedWith { a, b ->
                        collator.compare(a.title, b.title)
                    }

                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            recipes = sorted,
                            errorMessage = null
                        )
                    }
                }
                .onFailure { _ ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = ERROR_LOAD_RECIPES
                        )
                    }
                }
        }
    }

    /**
     * Regra de negócio de acesso a receitas.
     *
     * Lógica:
     * - LOGADO + COM_PLANO → pode abrir qualquer receita
     * - NAO_LOGADO ou LOGADO + SEM_PLANO → apenas receitas com isFreePreview = true
     *
     * @param recipe Receita a verificar acesso
     * @return true se usuário pode abrir, false caso contrário
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
     * Limpa mensagem de erro após exibição na UI.
     */
    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}

/**
 * Estado da UI para lista de receitas de uma categoria.
 *
 * @property isLoading Indica carregamento em andamento
 * @property currentCategoryId ID da categoria atual
 * @property recipes Lista de receitas ordenadas alfabeticamente
 * @property sessionState Estado de autenticação e plano do usuário
 * @property errorMessage Código de erro técnico (ou null)
 */
data class RecipeListUiState(
    val isLoading: Boolean = false,
    val currentCategoryId: String? = null,
    val recipes: List<Recipe> = emptyList(),
    val sessionState: UserSessionState,
    val errorMessage: String? = null
)