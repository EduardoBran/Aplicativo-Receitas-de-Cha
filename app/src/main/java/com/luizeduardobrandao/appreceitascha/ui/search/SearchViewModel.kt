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

/**
 * ViewModel responsável pela busca de receitas.
 *
 * Funcionalidades:
 * - Validação de query (mínimo 3 caracteres)
 * - Busca paralela de receitas e categorias
 * - Agrupamento de resultados por categoria (ordenado A-Z)
 * - Controle de acesso baseado em sessão do usuário
 */
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val recipeRepository: RecipeRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    companion object {
        private const val ERROR_VALIDATION_MIN_CHARS = "SEARCH_MIN_CHARS"
        private const val ERROR_SEARCH_FAILED = "SEARCH_FAILED"
    }

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    init {
        loadSession()
    }

    /**
     * Carrega estado de sessão do usuário (auth + plano).
     */
    private fun loadSession() {
        viewModelScope.launch {
            val result = authRepository.getCurrentUserSessionState()
            val session = result.getOrElse {
                UserSessionState(AuthState.NAO_LOGADO, PlanState.SEM_PLANO)
            }
            _uiState.update { it.copy(sessionState = session) }
        }
    }

    /**
     * Realiza busca de receitas por query.
     *
     * Validações:
     * - Mínimo 3 caracteres (trim aplicado)
     *
     * Fluxo:
     * 1. Busca paralela: receitas + categorias
     * 2. Agrupa receitas por categoria
     * 3. Ordena categorias e receitas alfabeticamente
     * 4. Monta lista polimórfica (Headers + RecipeItems)
     *
     * @param query Texto de busca digitado pelo usuário
     */
    fun performSearch(query: String) {
        // Validação: Mínimo 3 caracteres
        if (query.trim().length < 3) {
            _uiState.update { it.copy(validationError = ERROR_VALIDATION_MIN_CHARS) }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    errorMessage = null,
                    validationError = null,
                    isEmpty = false
                )
            }

            // Busca paralela: Receitas e Categorias (para pegar nomes)
            val categoriesDeferred = async { recipeRepository.getCategories() }
            val recipesDeferred = async { recipeRepository.searchRecipes(query) }

            val categoriesResult = categoriesDeferred.await()
            val recipesResult = recipesDeferred.await()

            if (recipesResult.isSuccess && categoriesResult.isSuccess) {
                val recipes = recipesResult.getOrNull() ?: emptyList()
                val categories = categoriesResult.getOrNull() ?: emptyList()

                if (recipes.isEmpty()) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            results = emptyList(),
                            isEmpty = true
                        )
                    }
                } else {
                    val groupedList = buildGroupedList(recipes, categories)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            results = groupedList,
                            isEmpty = false
                        )
                    }
                }
            } else {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = ERROR_SEARCH_FAILED
                    )
                }
            }
        }
    }

    /**
     * Constrói lista polimórfica agrupada por categoria.
     *
     * Estrutura:
     * - Header (nome da categoria, com divider opcional)
     * - RecipeItems (receitas ordenadas A-Z)
     *
     * Ordenação:
     * - Categorias: A-Z por nome
     * - Receitas dentro da categoria: A-Z por título
     *
     * @param recipes Lista de receitas retornadas pela busca
     * @param categories Lista completa de categorias (para pegar nomes)
     * @return Lista polimórfica de Headers + RecipeItems
     */
    private fun buildGroupedList(
        recipes: List<Recipe>,
        categories: List<Category>
    ): List<SearchListItem> {
        val uiList = mutableListOf<SearchListItem>()

        // 1. Mapa para recuperar categoria pelo ID
        val catMap = categories.associateBy { it.id }

        // 2. Agrupa receitas por ID da categoria
        val recipesByCatId = recipes.groupBy { it.categoryId }

        // 3. Ordena categorias alfabeticamente pelo nome
        val sortedCategories = recipesByCatId.keys
            .mapNotNull { catId -> catMap[catId] }
            .sortedBy { it.name }

        // 4. Itera sobre categorias ordenadas
        sortedCategories.forEachIndexed { index, category ->
            val catRecipes = recipesByCatId[category.id] ?: return@forEachIndexed

            // 5. Ordena receitas dentro da categoria alfabeticamente
            val sortedRecipes = catRecipes.sortedBy { it.title }

            // Divider: exibe se não for o primeiro grupo
            val showDivider = index > 0

            // Adiciona Header da categoria
            uiList.add(SearchListItem.Header(category.name, showDivider))

            // Adiciona receitas ordenadas
            sortedRecipes.forEach { recipe ->
                uiList.add(SearchListItem.RecipeItem(recipe))
            }
        }
        return uiList
    }

    /**
     * Verifica se usuário pode abrir uma receita.
     *
     * Regras:
     * - LOGADO + COM_PLANO → pode abrir qualquer receita
     * - NAO_LOGADO ou SEM_PLANO → apenas receitas com isFreePreview = true
     *
     * @param recipe Receita a verificar acesso
     * @return true se pode abrir, false caso contrário
     */
    fun canOpenRecipe(recipe: Recipe): Boolean {
        val session = _uiState.value.sessionState ?: return recipe.isFreePreview
        return when {
            session.authState == AuthState.LOGADO &&
                    session.planState == PlanState.COM_PLANO -> true

            else -> recipe.isFreePreview
        }
    }

    /**
     * Limpa erro de validação após exibição na UI.
     */
    fun clearValidationError() {
        _uiState.update { it.copy(validationError = null) }
    }
}

/**
 * Estado da UI de busca.
 *
 * @property isLoading Indica busca em andamento
 * @property results Lista polimórfica de Headers + RecipeItems
 * @property isEmpty Indica que busca não retornou resultados
 * @property sessionState Estado de autenticação e plano do usuário
 * @property errorMessage Código de erro técnico (ou null)
 * @property validationError Código de erro de validação (ou null)
 */
data class SearchUiState(
    val isLoading: Boolean = false,
    val results: List<SearchListItem> = emptyList(),
    val isEmpty: Boolean = false,
    val sessionState: UserSessionState? = null,
    val errorMessage: String? = null,
    val validationError: String? = null
)

/**
 * Itens polimórficos da lista de busca.
 *
 * Tipos:
 * - Header: Nome da categoria + flag de divider
 * - RecipeItem: Card de receita
 */
sealed class SearchListItem {
    /**
     * Header de categoria.
     *
     * @property categoryName Nome da categoria a exibir
     * @property showDivider Se deve exibir divider acima do header
     */
    data class Header(val categoryName: String, val showDivider: Boolean) : SearchListItem()

    /**
     * Item de receita.
     *
     * @property recipe Dados da receita
     */
    data class RecipeItem(val recipe: Recipe) : SearchListItem()
}