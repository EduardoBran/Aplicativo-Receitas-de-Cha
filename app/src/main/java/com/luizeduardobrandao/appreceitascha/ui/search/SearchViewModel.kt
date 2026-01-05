package com.luizeduardobrandao.appreceitascha.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luizeduardobrandao.appreceitascha.domain.auth.AuthRepository
import com.luizeduardobrandao.appreceitascha.domain.auth.AuthState
import com.luizeduardobrandao.appreceitascha.domain.auth.PlanState
import com.luizeduardobrandao.appreceitascha.domain.auth.UserSessionState
import com.luizeduardobrandao.appreceitascha.domain.recipes.Category
import com.luizeduardobrandao.appreceitascha.domain.recipes.Recipe
import com.luizeduardobrandao.appreceitascha.domain.recipes.RecipeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchUiState(
    val isLoading: Boolean = false,
    val results: List<SearchListItem> = emptyList(), // Lista polimórfica (Header ou Recipe)
    val isEmpty: Boolean = false,
    val sessionState: UserSessionState? = null,
    val errorMessage: String? = null,
    val validationError: String? = null // Erro de validação (< 3 chars)
)

/**
 * Itens da lista de busca (Header de categoria ou Card de receita)
 */
sealed class SearchListItem {
    data class Header(val categoryName: String, val showDivider: Boolean) : SearchListItem()
    data class RecipeItem(val recipe: Recipe) : SearchListItem()
}

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val recipeRepository: RecipeRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    init {
        loadSession()
    }

    private fun loadSession() {
        viewModelScope.launch {
            val result = authRepository.getCurrentUserSessionState()
            val session = result.getOrElse {
                UserSessionState(AuthState.NAO_LOGADO, PlanState.SEM_PLANO)
            }
            _uiState.update { it.copy(sessionState = session) }
        }
    }

    fun performSearch(query: String) {
        // Validação: Mínimo 3 caracteres
        if (query.trim().length < 3) {
            _uiState.update { it.copy(validationError = "MIN_CHARS") }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(isLoading = true, errorMessage = null, validationError = null, isEmpty = false)
            }

            // Busca paralela: Receitas e Categorias (para pegar os nomes)
            val categoriesDeferred = async { recipeRepository.getCategories() }
            val recipesDeferred = async { recipeRepository.searchRecipes(query) }

            val categoriesResult = categoriesDeferred.await()
            val recipesResult = recipesDeferred.await()

            if (recipesResult.isSuccess && categoriesResult.isSuccess) {
                val recipes = recipesResult.getOrNull() ?: emptyList()
                val categories = categoriesResult.getOrNull() ?: emptyList()

                if (recipes.isEmpty()) {
                    _uiState.update { it.copy(isLoading = false, results = emptyList(), isEmpty = true) }
                } else {
                    val groupedList = buildGroupedList(recipes, categories)
                    _uiState.update { it.copy(isLoading = false, results = groupedList, isEmpty = false) }
                }
            } else {
                _uiState.update { it.copy(isLoading = false, errorMessage = "Erro na busca.") }
            }
        }
    }

    private fun buildGroupedList(recipes: List<Recipe>, categories: List<Category>): List<SearchListItem> {
        val uiList = mutableListOf<SearchListItem>()

        // 1. Mapa para recuperar o objeto Categoria pelo ID (para ter acesso ao Nome)
        val catMap = categories.associateBy { it.id }

        // 2. Agrupa receitas por ID da categoria
        val recipesByCatId = recipes.groupBy { it.categoryId }

        // 3. Ordena as Categorias alfabeticamente pelo Nome
        // - recipesByCatId.keys pega os IDs das categorias encontradas na busca
        // - mapNotNull converte ID -> Objeto Category
        // - sortedBy ordena A-Z pelo nome da categoria
        val sortedCategories = recipesByCatId.keys
            .mapNotNull { catId -> catMap[catId] }
            .sortedBy { it.name }

        // 4. Itera sobre as categorias já ordenadas
        sortedCategories.forEachIndexed { index, category ->
            val catRecipes = recipesByCatId[category.id] ?: return@forEachIndexed

            // 5. Ordena as Receitas dentro desta categoria alfabeticamente pelo Título
            val sortedRecipes = catRecipes.sortedBy { it.title }

            // Regra do Divider: Exibe se não for o primeiro grupo
            val showDivider = index > 0

            // Adiciona o Header da Categoria
            uiList.add(SearchListItem.Header(category.name, showDivider))

            // Adiciona as Receitas ordenadas
            sortedRecipes.forEach { recipe ->
                uiList.add(SearchListItem.RecipeItem(recipe))
            }
        }
        return uiList
    }

    fun canOpenRecipe(recipe: Recipe): Boolean {
        val session = _uiState.value.sessionState ?: return recipe.isFreePreview
        return when {
            session.authState == AuthState.LOGADO &&
                    session.planState == PlanState.COM_PLANO -> true
            else -> recipe.isFreePreview
        }
    }

    fun clearValidationError() {
        _uiState.update { it.copy(validationError = null) }
    }
}