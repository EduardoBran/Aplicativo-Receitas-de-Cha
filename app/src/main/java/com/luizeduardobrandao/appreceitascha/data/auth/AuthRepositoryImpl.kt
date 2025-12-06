package com.luizeduardobrandao.appreceitascha.data.auth

import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.userProfileChangeRequest
import com.google.firebase.database.FirebaseDatabase
import com.luizeduardobrandao.appreceitascha.data.local.SessionManager
import com.luizeduardobrandao.appreceitascha.domain.auth.PlanState
import com.luizeduardobrandao.appreceitascha.domain.auth.PlanType
import com.luizeduardobrandao.appreceitascha.domain.auth.PlanConstants
import com.luizeduardobrandao.appreceitascha.domain.auth.toPlanId
import com.luizeduardobrandao.appreceitascha.domain.auth.toPlanType
import com.luizeduardobrandao.appreceitascha.domain.auth.AuthRepository
import com.luizeduardobrandao.appreceitascha.domain.auth.AuthState
import com.luizeduardobrandao.appreceitascha.domain.auth.User
import com.luizeduardobrandao.appreceitascha.domain.auth.UserPlan
import com.luizeduardobrandao.appreceitascha.domain.auth.UserSessionState
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/** Implementação concreta do [AuthRepository] utilizando [FirebaseAuthDataSource]
 *  e [FirebaseDatabase] para autenticação e persistência de dados do usuário.
 *
 *  Camadas de responsabilidade:
 *  - FirebaseAuth (via [FirebaseAuthDataSource]):
 *      • login, cadastro, logout, reset de senha.
 *  - Realtime Database:
 *      • /users/{uid}     → perfil básico (nome, e-mail, telefone, verificação de e-mail).
 *      • /userPlans/{uid} → status de plano (SEM_PLANO / COM_PLANO, datas e tipo do plano).
 *  A camada de domínio/UI enxerga apenas [AuthRepository], [User], [UserPlan] e [UserSessionState]. */
@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val authDataSource: FirebaseAuthDataSource,
    private val database: FirebaseDatabase,
    private val sessionManager: SessionManager
) : AuthRepository {

    /** Login delegando ao [FirebaseAuthDataSource] e mapeando o [FirebaseUser] para [User]. */
    override suspend fun login(email: String, password: String): Result<User> {
        return authDataSource
            .signIn(email, password)
            .map { firebaseUser -> firebaseUser.toDomainUser() }
    }

    /** Cadastro de usuário:
     *  1) Cria o usuário no FirebaseAuth (e-mail/senha)
     *  2) Atualiza o displayName (já feito no DataSource)
     *  3) Salva o perfil no Realtime Database em /users/{uid}
     *
     * @param name Nome informado na tela de cadastro.
     * @param email E-mail do usuário.
     * @param password Senha.
     * @param phone Telefone opcional (pode ser null). */
    override suspend fun register(
        name: String, email: String, password: String, phone: String?
    ): Result<User> {
        // 1) Cria o usuário no FirebaseAuth
        val signUpResult = authDataSource.signUp(name, email, password, phone)
        if (signUpResult.isFailure) {
            return Result.failure(
                signUpResult.exceptionOrNull()
                    ?: Exception("Erro desconhecido ao cadastrar usuário.")
            )
        }
        val firebaseUser = signUpResult.getOrThrow()
        // 2) Converte para o modelo de domínio User
        return try {
            val user = firebaseUser.toDomainUser(
                explicitName = name, explicitPhone = phone
            )
            // 3) Configura no Firebase: /users/{uid} -> perfil básico
            val userRef = database.getReference("users")
                .child(user.uid)
            val userData = mapOf(
                "uid" to user.uid,
                "name" to (user.name ?: ""),
                "email" to user.email,
                "phone" to (user.phone ?: ""),
                "emailVerified" to user.isEmailVerified
            )

            // Salva perfil
            userRef.setValue(userData).await()

            // 4) /userPlans/{uid} -> pré-cadastra SEM_PLANO
            // Regra de negócio:
            // - Usuário recém-cadastrado = LOGADO + SEM_PLANO.
            // - Representamos SEM_PLANO no banco como:
            //     planId = "none"
            //     expiresAt = null
            //     isLifetime = false
            // Quando o Billing aprovar uma compra, "updateUserPlan(...)
            // vai sobrescrever esses campos com o plano real.
            val userPlanRef = database.getReference("userPlans")
                .child(user.uid)

            val initialPlanData = mapOf(
                "planId" to PlanConstants.PLAN_ID_NONE,
                "expiresAt" to null,
                "isLifetime" to false
            )
            userPlanRef.setValue(initialPlanData).await()

            // 5) Retorna sucesso com User
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Encapsula o envio de e-mail de reset de senha. */
    override suspend fun sendPasswordReset(email: String): Result<Unit> {
        return authDataSource.sendPasswordResetEmail(email)
    }

    /** Lê o usuário logado a partir do FirebaseAuth e converte para [User]. */
    override fun getCurrentUser(): User? {
        val firebaseUser = authDataSource.getCurrentFirebaseUser()
        return firebaseUser?.toDomainUser()
    }

    /** Efetua logout delegando ao DataSource. */
    override fun logout() {
        authDataSource.logout()
        sessionManager.resetWelcomeFlag()
    }

    /** PLANOS DO USUÁRIO
     * Lê /userPlans/{uid} no Realtime Database.
     * - Se não existir, retorna Result.success(null) => SEM_PLANO.
     * - Se existir, converte para [UserPlan]. */
    override suspend fun getUserPlan(uid: String): Result<UserPlan?> {
        return try {
            val ref = database.getReference("userPlans").child(uid)
            val snapshot = ref.get().await()

            if (!snapshot.exists()) {
                // Usuário ainda não possui plano cadastrado
                Result.success(null)
            } else {
                val planId = snapshot.child("planId").getValue(String::class.java)
                val expiresAt = snapshot.child("expiresAt").getValue(Long::class.java)
                val isLifetime = snapshot.child("isLifetime").getValue(Boolean::class.java) ?: false

                val planType = planId.toPlanType()

                Result.success(
                    UserPlan(
                        uid = uid,
                        planType = planType,
                        expiresAtMillis = expiresAt,
                        isLifetime = isLifetime
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Atualiza /userPlans/{uid} com os dados de [UserPlan].
     * Será chamado após uma compra aprovada ou sincronização com a Play Store. */
    override suspend fun updateUserPlan(plan: UserPlan): Result<Unit> {
        return try {
            val ref = database.getReference("userPlans").child(plan.uid)

            val data = hashMapOf<String, Any?>(
                "planId" to plan.planType.toPlanId(),        // SEM_PLANO -> "none"
                "expiresAt" to plan.expiresAtMillis,         // null para vitalício ou NONE
                "isLifetime" to plan.isLifetime              // false para NONE / planos temporários
            )

            ref.setValue(data).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Retorna a combinação de estados (autenticação + plano) para a sessão atual. */
    override suspend fun getCurrentUserSessionState(): Result<UserSessionState> {
        val firebaseUser = authDataSource.getCurrentFirebaseUser()
            ?: return Result.success(
                UserSessionState(
                    authState = AuthState.NAO_LOGADO,
                    planState = PlanState.SEM_PLANO,
                    planType = PlanType.NONE
                )
            )

        val uid = firebaseUser.uid
        val planResult = getUserPlan(uid)

        return planResult.map { plan ->
            val now = System.currentTimeMillis()

            // Se não houver plano no banco, consideramos NONE
            val effectivePlanType = plan?.planType ?: PlanType.NONE

            val planState =
                if (plan == null || effectivePlanType == PlanType.NONE) {
                    PlanState.SEM_PLANO
                } else if (
                    !plan.isLifetime &&
                    plan.expiresAtMillis != null &&
                    plan.expiresAtMillis < now
                ) {
                    // Plano temporário expirado
                    PlanState.SEM_PLANO
                } else {
                    PlanState.COM_PLANO
                }

            UserSessionState(
                authState = AuthState.LOGADO,
                planState = planState,
                planType = effectivePlanType
            )
        }
    }

    /** Atualiza nome e telefone do usuário */
    override suspend fun updateUserProfile(
        uid: String,
        name: String,
        phone: String?
    ): Result<Unit> {
        return try {
            // 1) Atualiza displayName no FirebaseAuth
            val currentFirebaseUser = authDataSource.getCurrentFirebaseUser()
            if (currentFirebaseUser != null && currentFirebaseUser.uid == uid) {
                val profileUpdates = userProfileChangeRequest {
                    displayName = name
                }
                currentFirebaseUser.updateProfile(profileUpdates).await()
            }

            // 2) Atualiza dados no Realtime Database /users/{uid}
            val userRef = database.getReference("users").child(uid)
            val updates = mapOf(
                "name" to name,
                "phone" to (phone ?: "")
            )
            userRef.updateChildren(updates).await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Reautentica o usuário com senha atual */
    override suspend fun reauthenticateUser(currentPassword: String): Result<Unit> {
        return authDataSource.reauthenticateWithPassword(currentPassword)
    }

    /** Atualiza e-mail (requer reautenticação prévia) */
    override suspend fun updateUserEmail(newEmail: String): Result<Unit> {
        val result = authDataSource.updateEmail(newEmail)

        // Se sucesso, atualiza também no Realtime Database
        if (result.isSuccess) {
            try {
                val uid = authDataSource.getCurrentFirebaseUser()?.uid
                if (uid != null) {
                    val userRef = database.getReference("users").child(uid)
                    userRef.child("email").setValue(newEmail).await()
                }
            } catch (e: Exception) {
                // Ignora erro de atualização no database, pois o principal já foi feito
            }
        }

        return result
    }

    /** Atualiza senha (requer reautenticação prévia) */
    override suspend fun updateUserPassword(newPassword: String): Result<Unit> {
        return authDataSource.updatePassword(newPassword)
    }

    /** Login com Google usando idToken */
    override suspend fun signInWithGoogle(idToken: String): Result<User> {
        val signInResult = authDataSource.signInWithGoogle(idToken)

        if (signInResult.isFailure) {
            return Result.failure(
                signInResult.exceptionOrNull()
                    ?: Exception("Erro desconhecido ao fazer login com Google.")
            )
        }

        val firebaseUser = signInResult.getOrThrow()

        return try {
            val user = firebaseUser.toDomainUser()

            // Verifica se o usuário já existe no Realtime Database
            val userRef = database.getReference("users").child(user.uid)
            val snapshot = userRef.get().await()

            if (!snapshot.exists()) {
                // Primeira vez - criar perfil no database
                val userData = mapOf(
                    "uid" to user.uid,
                    "name" to (user.name ?: ""),
                    "email" to user.email,
                    "phone" to (user.phone ?: ""),
                    "emailVerified" to true // Google já verifica
                )
                userRef.setValue(userData).await()

                // Criar plano inicial
                val userPlanRef = database.getReference("userPlans").child(user.uid)
                val initialPlanData = mapOf(
                    "planId" to PlanConstants.PLAN_ID_NONE,
                    "expiresAt" to null,
                    "isLifetime" to false
                )
                userPlanRef.setValue(initialPlanData).await()
            }

            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Envia e-mail de verificação */
    override suspend fun sendEmailVerification(): Result<Unit> {
        return authDataSource.sendEmailVerification()
    }
}

/** Função de extensão para converter um [FirebaseUser] em [User] (modelo de domínio).
 * @param explicitName Nome informado explicitamente no fluxo (por exemplo, na tela de cadastro).
 * @param explicitPhone Telefone lido do formulário. Se null, tentamos usar o phoneNumber do Firebase */
private fun FirebaseUser.toDomainUser(
    explicitName: String? = null,
    explicitPhone: String? = null
): User {
    // Captura o provider principal do usuário
    // providerData[0] é geralmente "firebase", então pegamos o primeiro provider real
    val provider = providerData.firstOrNull { it.providerId != "firebase" }?.providerId

    return User(
        uid = uid,
        name = explicitName ?: displayName,
        email = email.orEmpty(),
        phone = explicitPhone ?: phoneNumber,
        isEmailVerified = isEmailVerified,
        provider = provider
    )
}