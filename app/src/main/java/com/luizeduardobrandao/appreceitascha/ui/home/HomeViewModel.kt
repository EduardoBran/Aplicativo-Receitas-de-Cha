package com.luizeduardobrandao.appreceitascha.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luizeduardobrandao.appreceitascha.domain.auth.AuthRepository
import com.luizeduardobrandao.appreceitascha.domain.auth.AuthState
import com.luizeduardobrandao.appreceitascha.domain.auth.PlanState
import com.luizeduardobrandao.appreceitascha.domain.auth.UserPlan
import com.luizeduardobrandao.appreceitascha.domain.auth.UserSessionState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel da Home.
 *
 * - Consulta o estado da sessão (NAO_LOGADO / LOGADO + SEM_PLANO / COM_PLANO).
 * - Quando COM_PLANO, carrega também o UserPlan para exibir nome do plano e expiração.
 */
data class HomeUiState(
    val isLoading: Boolean = true,
    val sessionState: UserSessionState? = null,
    val userPlan: UserPlan? = null,
    val errorMessage: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        refreshSession()
    }

    /**
     * Recarrega o estado da sessão e, se for LOGADO + COM_PLANO, carrega o UserPlan.
     */
    fun refreshSession() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            val sessionResult = authRepository.getCurrentUserSessionState()

            sessionResult.fold(
                onSuccess = { session ->
                    // Se não está logado ou não tem plano, não precisamos buscar /userPlans.
                    if (session.authState != AuthState.LOGADO ||
                        session.planState != PlanState.COM_PLANO
                    ) {
                        _uiState.value = HomeUiState(
                            isLoading = false,
                            sessionState = session,
                            userPlan = null,
                            errorMessage = null
                        )
                        return@fold
                    }

                    // LOGADO + COM_PLANO → buscar UserPlan no Realtime Database
                    val currentUser = authRepository.getCurrentUser()
                    if (currentUser == null) {
                        _uiState.value = HomeUiState(
                            isLoading = false,
                            sessionState = session,
                            userPlan = null,
                            errorMessage = null
                        )
                        return@fold
                    }

                    val planResult = authRepository.getUserPlan(currentUser.uid)

                    val userPlan = planResult.getOrNull()

                    _uiState.value = HomeUiState(
                        isLoading = false,
                        sessionState = session,
                        userPlan = userPlan,
                        errorMessage = null
                    )
                },
                onFailure = { error ->
                    _uiState.value = HomeUiState(
                        isLoading = false,
                        sessionState = null,
                        userPlan = null,
                        errorMessage = error.message ?: "Erro ao carregar estado do usuário."
                    )
                }
            )
        }
    }
}