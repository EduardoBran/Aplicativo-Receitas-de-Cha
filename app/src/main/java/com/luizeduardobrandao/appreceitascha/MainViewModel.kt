package com.luizeduardobrandao.appreceitascha

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luizeduardobrandao.appreceitascha.domain.auth.AuthRepository
import com.luizeduardobrandao.appreceitascha.domain.auth.AuthState
import com.luizeduardobrandao.appreceitascha.domain.auth.PlanState
import com.luizeduardobrandao.appreceitascha.domain.auth.PlanType
import com.luizeduardobrandao.appreceitascha.domain.auth.User
import com.luizeduardobrandao.appreceitascha.domain.auth.UserSessionState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class MainViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    // Usuário atual (para ícone do bottom nav, etc.)
    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    // Estado completo da sessão (auth + plano) vindo do AuthRepository
    private val _sessionState = MutableStateFlow(
        UserSessionState(
            authState = AuthState.NAO_LOGADO,
            planState = PlanState.SEM_PLANO,
            planType = PlanType.NONE
        )
    )
    val sessionState: StateFlow<UserSessionState> = _sessionState.asStateFlow()

    init {
        observeAuthAndSessionOnce()
        startPeriodicCheck()
    }

    /**
     * Carrega usuário atual e estado de sessão na inicialização.
     */
    private fun observeAuthAndSessionOnce() {
        viewModelScope.launch {
            refreshAuthAndSession()
        }
    }

    /**
     * Verifica periodicamente apenas o usuário logado.
     * (Usado para manter ícone de login/perfil atualizado sem bombardear o Firebase.)
     */
    private fun startPeriodicCheck() {
        viewModelScope.launch {
            while (true) {
                delay(500) // Verifica a cada 500ms
                _currentUser.value = authRepository.getCurrentUser()
                // Aqui NÃO chamamos getCurrentUserSessionState() para não sobrecarregar o Realtime Database.
            }
        }
    }

    /**
     * Atualiza usuário atual + estado de sessão (auth + plano) de forma sincronizada.
     * Chamado:
     * - na inicialização
     * - em onResume() da Activity
     * - pode ser chamado também após login/cadastro/pagamento, se necessário.
     */
    private suspend fun refreshAuthAndSession() {
        // Usuário atual (FirebaseAuth em memória)
        _currentUser.value = authRepository.getCurrentUser()

        // Estado de sessão completo (lê /userPlans/{uid} quando necessário)
        val result = authRepository.getCurrentUserSessionState()
        result.onSuccess { state ->
            _sessionState.value = state
        }.onFailure {
            // Em caso de erro, mantemos o estado anterior para não quebrar a UI.
            // Opcionalmente, você poderia forçar:
            // _sessionState.value = UserSessionState(AuthState.NAO_LOGADO, PlanState.SEM_PLANO, PlanType.NONE)
        }
    }

    /**
     * Chamada pública usada pela Activity (ex.: onResume) para atualizar tudo.
     */
    fun refreshAuthState() {
        viewModelScope.launch {
            refreshAuthAndSession()
        }
    }

    fun isUserLoggedIn(): Boolean {
        return _currentUser.value != null
    }
}