package com.luizeduardobrandao.appreceitascha.domain.auth

/**  Contrato da camada de domínio para autenticação e plano de acesso.
 *   - A ViewModel conversa SOMENTE com essa interface, sem depender diretamente de
 *     FirebaseAuth, FirebaseDatabase ou qualquer SDK específico.
 *   - Utiliza [Result] para encapsular sucesso/erro padronizado, para tratar exceções na UI */
interface AuthRepository {
    /** Login com e-mail e senha.
     *   @param email E-mail do usuário.
     *   @param password Senha do usuário.
     *   @return [Result] contendo o [User] autenticado ou erro. */
    suspend fun login(
        email: String, password: String
    ): Result<User>

    /** Cadastro de usuário com e-mail e senha.
     *   @param name Nome que será associado ao usuário (displayName no Firebase).
     *   @param email E-mail do usuário.
     *   @param password Senha do usuário.
     *   @param phone Telefone opcional.
     *   @return [Result] contendo o [User] criado ou erro. */
    suspend fun register(
        name: String, email: String,
        password: String, phone: String?
    ): Result<User>

    /** Envia e-mail de recuperação de senha.
     *   @param email E-mail que receberá o link de redefinição.
     *   @return [Result] com [Unit] em caso de sucesso ou erro. */
    suspend fun sendPasswordReset(email: String): Result<Unit>

    /** Retorna o usuário atual logado, caso exista.
     *  - Esta operação é síncrona porque o Firebase mantém o usuário em memória localmente. */
    fun getCurrentUser(): User?

    /** Efetua logout do usuário atual. Também é uma operação síncrona no FirebaseAuth. */
    fun logout()

    /** PLANOS DO USUÁRIO
     *   - Recupera o plano atual do usuário pelo [uid].
     *     @return [Result] com [UserPlan] ou null quando o usuário está SEM_PLANO. */
    suspend fun getUserPlan(uid: String): Result<UserPlan?>

    /** Atualiza o plano do usuário em /userPlans/{uid}.
     *  - Será usado pelo fluxo de compra (PlanosFragment / Billing). */
    suspend fun updateUserPlan(plan: UserPlan): Result<Unit>

    /** Retorna a combinação de estados de autenticação e plano para a sessão atual.
     *   - Usado pela UI para decidir qual card mostrar na Home,
     *     e o que liberar em Recipes e Favorites. */
    suspend fun getCurrentUserSessionState(): Result<UserSessionState>

    /** ATUALIZAÇÃO DE PERFIL */

    /** Atualiza apenas nome e telefone do usuário.
     * @param uid ID do usuário
     * @param name Nome atualizado
     * @param phone Telefone atualizado (opcional)
     * @return Result com sucesso ou erro */
    suspend fun updateUserProfile(uid: String, name: String, phone: String?): Result<Unit>

    /** Reautentica o usuário com sua senha atual.
     * Necessário antes de alterar e-mail ou senha por segurança.
     * @param currentPassword Senha atual do usuário
     * @return Result com sucesso ou erro */
    suspend fun reauthenticateUser(currentPassword: String): Result<Unit>

    /** Altera o e-mail do usuário (requer reautenticação prévia).
     * @param newEmail Novo e-mail
     * @return Result com sucesso ou erro */
    suspend fun updateUserEmail(newEmail: String): Result<Unit>

    /** Altera a senha do usuário (requer reautenticação prévia).
     * @param newPassword Nova senha
     * @return Result com sucesso ou erro */
    suspend fun updateUserPassword(newPassword: String): Result<Unit>

    /** Login com Google usando idToken.
     * @param idToken Token de identificação do Google
     * @return Result com User autenticado ou erro */
    suspend fun signInWithGoogle(idToken: String): Result<User>

    /** Envia e-mail de verificação para o usuário atual.
     * Usado após cadastro com e-mail/senha.
     * @return Result com sucesso ou erro */
    suspend fun sendEmailVerification(): Result<Unit>
}