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
            var lastUid: String? = null

            while (true) {
                delay(500)

                val user = authRepository.getCurrentUser()
                _currentUser.value = user

                val newUid = user?.uid
                if (newUid != lastUid) {
                    lastUid = newUid
                    // Isso atualiza _sessionState também (e vai virar NAO_LOGADO ao deslogar)
                    refreshAuthAndSession()
                }
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
        val currentUser = authRepository.getCurrentUser()
        _currentUser.value = currentUser

        val result = authRepository.getCurrentUserSessionState()

        result.onSuccess { state ->

            _sessionState.value = state
        }.onFailure { error ->

            if (currentUser != null) {
                _sessionState.value = UserSessionState(
                    authState = AuthState.LOGADO,
                    planState = PlanState.SEM_PLANO,
                    planType = PlanType.NONE
                )
            } else {
                _sessionState.value = UserSessionState(
                    authState = AuthState.NAO_LOGADO,
                    planState = PlanState.SEM_PLANO,
                    planType = PlanType.NONE
                )
            }
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