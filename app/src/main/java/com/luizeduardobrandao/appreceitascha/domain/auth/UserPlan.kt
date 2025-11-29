package com.luizeduardobrandao.appreceitascha.domain.auth

/**
 * Tipo de plano disponível no app.
 *
 * NONE representa SEM_PLANO (nunca comprou ou plano expirado).
 */
enum class PlanType {
    NONE,
    PLAN_3M,
    PLAN_6M,
    PLAN_12M,
    PLAN_LIFE
}

/**
 * Informações de plano de um usuário.
 *
 * Espelha a estrutura salva em /userPlans/{uid} no Realtime Database.
 */
data class UserPlan(
    val uid: String,
    val planType: PlanType,
    val expiresAtMillis: Long?,  // null para vitalício ou SEM_PLANO
    val isLifetime: Boolean
)

/**
 * Estado de autenticação na sessão atual.
 */
enum class AuthState {
    NAO_LOGADO,
    LOGADO
}

/**
 * Estado de plano na sessão atual.
 */
enum class PlanState {
    SEM_PLANO,
    COM_PLANO
}

/**
 * Combinação de estados usada pela UI para decidir o que exibir.
 *
 * Exemplos:
 * - NAO_LOGADO + SEM_PLANO  -> Visitante
 * - LOGADO + SEM_PLANO      -> Usuário sem plano
 * - LOGADO + COM_PLANO      -> Usuário com plano ativo
 */
data class UserSessionState(
    val authState: AuthState,
    val planState: PlanState
)