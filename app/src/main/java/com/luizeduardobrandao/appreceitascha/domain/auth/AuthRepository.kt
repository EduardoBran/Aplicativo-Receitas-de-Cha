package com.luizeduardobrandao.appreceitascha.domain.auth

/**
 * Contrato da camada de domínio para autenticação.
 *
 * A ViewModel conversa SOMENTE com essa interface, sem depender diretamente do Firebase.
 *
 * Utilizamos [Result] para encapsular sucesso/erro de forma padronizada,
 * permitindo tratamento de exceções na camada de UI.
 */
interface AuthRepository {
    /**
     * Login com e-mail e senha.
     *
     * @param email E-mail do usuário.
     * @param password Senha do usuário.
     * @return [Result] contendo o [User] autenticado ou erro.
     */
    suspend fun login(
        email: String,
        password: String
    ): Result<User>

    /**
     * Cadastro de usuário com e-mail e senha.
     *
     * @param name Nome que será associado ao usuário (displayName no Firebase).
     * @param email E-mail do usuário.
     * @param password Senha do usuário.
     * @param phone Telefone opcional. (Idealmente salvo em Firestore/Realtime DB futuramente.)
     * @return [Result] contendo o [User] criado ou erro.
     */
    suspend fun register(
        name: String,
        email: String,
        password: String,
        phone: String?
    ): Result<User>

    /**
     * Envia e-mail de recuperação de senha.
     *
     * @param email E-mail que receberá o link de redefinição.
     * @return [Result] com [Unit] em caso de sucesso ou erro.
     */
    suspend fun sendPasswordReset(email: String): Result<Unit>

    /**
     * Retorna o usuário atual logado, caso exista.
     *
     * Esta operação é síncrona porque o Firebase mantém o usuário em memória localmente.
     */
    fun getCurrentUser(): User?

    /**
     * Efetua logout do usuário atual. Também é uma operação síncrona no FirebaseAuth.
     */
    fun logout()
}