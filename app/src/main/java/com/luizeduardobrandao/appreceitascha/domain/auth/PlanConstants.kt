package com.luizeduardobrandao.appreceitascha.domain.auth

/**
 * Constantes de IDs de produto/plano usados tanto no Billing
 * quanto no Realtime Database (/userPlans/{uid}).
 */
object PlanConstants {
    const val PLAN_ID_NONE = "none"
    const val PLAN_ID_LIFE = "plan_life"
}

/**
 * Converte o ID salvo no Firebase / Play Console para [PlanType].
 */
fun String?.toPlanType(): PlanType = when (this) {
    PlanConstants.PLAN_ID_LIFE -> PlanType.PLAN_LIFE
    else -> PlanType.NONE
}

/**
 * Converte um [PlanType] para o ID que será salvo em /userPlans
 * e usado como productId no Billing.
 *
 * SEM_PLANO é representado de forma explícita com "none".
 */
fun PlanType.toPlanId(): String = when (this) {
    PlanType.PLAN_LIFE -> PlanConstants.PLAN_ID_LIFE
    PlanType.NONE -> PlanConstants.PLAN_ID_NONE
}