package com.luizeduardobrandao.appreceitascha.domain.auth

/**
 * Modelo de usuário da camada de domínio.
 *
 * Essa classe representa o usuário na aplicação, independente de
 * qual serviço de backend está sendo usado (Firebase, API própria, etc).
 */
data class User(
    /** UID único fornecido pelo backend (no caso, FirebaseAuth). */
    val uid: String,

    /** Nome do usuário (pode vir do displayName do Firebase ou de outro backend). */
    val name: String?,

    /** E-mail principal do usuário. */
    val email: String,

    /** Telefone opcional. Idealmente salvo na base de dados de perfil. */
    val phone: String?,

    /** Indica se o e-mail do usuário já foi verificado. */
    val isEmailVerified: Boolean,

    /** Provider de autenticação: "password", "google.com", "facebook.com", etc. */
    val provider: String? = null,

    /** URL da foto ao realizar login via Google (quando existir) */
    val photoUrl: String? = null
)