package com.luizeduardobrandao.appreceitascha

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luizeduardobrandao.appreceitascha.domain.auth.AuthRepository
import com.luizeduardobrandao.appreceitascha.domain.auth.User
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    init {
        observeAuthState()
        startPeriodicCheck()
    }

    private fun observeAuthState() {
        viewModelScope.launch {
            // Carrega o usuário atual imediatamente
            _currentUser.value = authRepository.getCurrentUser()
        }
    }

    /**
     * Verifica periodicamente o estado de autenticação
     * Necessário porque o Firebase não emite eventos automáticos de logout
     */
    private fun startPeriodicCheck() {
        viewModelScope.launch {
            while (true) {
                delay(500) // Verifica a cada 500ms
                _currentUser.value = authRepository.getCurrentUser()
            }
        }
    }

    fun refreshAuthState() {
        _currentUser.value = authRepository.getCurrentUser()
    }

    fun isUserLoggedIn(): Boolean {
        return _currentUser.value != null
    }
}