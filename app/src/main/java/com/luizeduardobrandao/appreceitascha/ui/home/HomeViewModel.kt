package com.luizeduardobrandao.appreceitascha.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luizeduardobrandao.appreceitascha.domain.auth.AuthRepository
import com.luizeduardobrandao.appreceitascha.domain.auth.PlanState
import com.luizeduardobrandao.appreceitascha.domain.auth.UserPlan
import com.luizeduardobrandao.appreceitascha.domain.auth.UserSessionState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val isLoading: Boolean = true,
    val sessionState: UserSessionState? = null,
    val userPlan: UserPlan? = null,
    val userName: String? = null,
    val userAvatarUrl: String? = null,
    val errorMessage: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    fun refreshSession() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            // 1. Busca Usuário e Sessão
            val currentUser = authRepository.getCurrentUser()
            val avatarUrl = currentUser?.photoUrl
            val sessionResult = authRepository.getCurrentUserSessionState()

            sessionResult.fold(
                onSuccess = { session ->
                    // ✅ Pega o nome atualizado do usuário
                    val currentName = currentUser?.name

                    if (session.planState == PlanState.SEM_PLANO) {
                        _uiState.value = HomeUiState(
                            isLoading = false,
                            sessionState = session,
                            userPlan = null,
                            userName = currentName, // ✅ Passa o nome
                            userAvatarUrl = avatarUrl,
                            errorMessage = null
                        )
                        return@fold
                    }

                    // Se tem plano, busca detalhes do plano
                    val planResult = currentUser?.uid?.let { uid ->
                        authRepository.getUserPlan(uid)
                    }

                    val userPlan = planResult?.getOrNull()

                    _uiState.value = HomeUiState(
                        isLoading = false,
                        sessionState = session,
                        userPlan = userPlan,
                        userName = currentName, // ✅ Passa o nome
                        userAvatarUrl = avatarUrl,
                        errorMessage = null
                    )
                },
                onFailure = { error ->
                    _uiState.value = HomeUiState(
                        isLoading = false,
                        sessionState = null,
                        userPlan = null,
                        userName = null,
                        errorMessage = error.message ?: "Erro ao carregar estado."
                    )
                }
            )
        }
    }
}