package com.luizeduardobrandao.appreceitascha.data.auth

import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.userProfileChangeRequest
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DataSource responsável por conversar diretamente com o FirebaseAuth.
 *
 * Aqui ficam APENAS operações de autenticação (login, cadastro,
 * recuperação de senha, etc.) retornando [FirebaseUser] ou [Result].
 *
 * A camada de domínio (AuthRepository) não depende deste tipo diretamente.
 */
@Singleton
class FirebaseAuthDataSource @Inject constructor(
    private val firebaseAuth: FirebaseAuth
) {

    /**
     * Faz login com e-mail e senha.
     *
     * @return [Result] com [FirebaseUser] autenticado ou exceção em caso de erro.
     */
    suspend fun signIn(
        email: String,
        password: String
    ): Result<FirebaseUser> {
        return try {
            val authResult = firebaseAuth
                .signInWithEmailAndPassword(email, password)
                .await()

            val user = authResult.user
                ?: return Result.failure(
                    IllegalStateException("Usuário Firebase retornou nulo após login.")
                )

            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Cria um novo usuário com e-mail e senha e, se possível,
     * atualiza o displayName no perfil do FirebaseAuth.
     *
     * @param name Nome a ser exibido no Firebase (displayName).
     * @param email E-mail do usuário.
     * @param password Senha do usuário.
     * @param phone Telefone opcional (armazenamento ideal em Firestore/Realtime).
     */
    suspend fun signUp(
        name: String,
        email: String,
        password: String,
        phone: String?
    ): Result<FirebaseUser> {
        return try {
            val authResult = firebaseAuth
                .createUserWithEmailAndPassword(email, password)
                .await()

            val user = authResult.user
                ?: return Result.failure(
                    IllegalStateException("Usuário Firebase retornou nulo após cadastro.")
                )

            // Atualiza o displayName do usuário no Firebase, se o nome não for vazio.
            if (name.isNotBlank()) {
                val profileUpdates = userProfileChangeRequest {
                    displayName = name
                }
                // Atualização do perfil também é assíncrona.
                user.updateProfile(profileUpdates).await()
            }

            // OBS: telefone (phone) idealmente deve ser persistido em outro serviço (Firestore, Realtime DB).
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Envia e-mail de redefinição de senha.
     */
    suspend fun sendPasswordResetEmail(email: String): Result<Unit> {
        return try {
            firebaseAuth.sendPasswordResetEmail(email).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Retorna o usuário atual logado no Firebase ou null.
     */
    fun getCurrentFirebaseUser(): FirebaseUser? = firebaseAuth.currentUser

    /**
     * Realiza logout limpando o usuário atual.
     */
    fun logout() {
        firebaseAuth.signOut()
    }

    /**
     * Reautentica o usuário atual com sua senha.
     * Necessário antes de operações sensíveis como alterar e-mail ou senha.
     */
    suspend fun reauthenticateWithPassword(currentPassword: String): Result<Unit> {
        return try {
            val user = firebaseAuth.currentUser
                ?: return Result.failure(IllegalStateException("Nenhum usuário logado."))

            val email = user.email
                ?: return Result.failure(IllegalStateException("E-mail do usuário não encontrado."))

            val credential = EmailAuthProvider.getCredential(email, currentPassword)
            user.reauthenticate(credential).await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Atualiza o e-mail do usuário com verificação automática.
     * IMPORTANTE: Usuário deve estar reautenticado recentemente.
     *
     * Esta função envia um e-mail de verificação para o novo endereço.
     * O e-mail só será atualizado após o usuário clicar no link de verificação.
     */
    suspend fun updateEmail(newEmail: String): Result<Unit> {
        return try {
            val user = firebaseAuth.currentUser
                ?: return Result.failure(IllegalStateException("Nenhum usuário logado."))

            user.verifyBeforeUpdateEmail(newEmail).await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Atualiza a senha do usuário.
     * IMPORTANTE: Usuário deve estar reautenticado recentemente.
     */
    suspend fun updatePassword(newPassword: String): Result<Unit> {
        return try {
            val user = firebaseAuth.currentUser
                ?: return Result.failure(IllegalStateException("Nenhum usuário logado."))

            user.updatePassword(newPassword).await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Faz login com Google usando o idToken obtido do GoogleSignIn.
     * Se for a primeira vez, cria automaticamente o usuário no Firebase.
     */
    suspend fun signInWithGoogle(idToken: String): Result<FirebaseUser> {
        return try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val authResult = firebaseAuth.signInWithCredential(credential).await()

            val user = authResult.user
                ?: return Result.failure(
                    IllegalStateException("Usuário Firebase retornou nulo após login com Google.")
                )

            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Envia e-mail de verificação para o usuário atual.
     * Usado após cadastro com e-mail/senha.
     */
    suspend fun sendEmailVerification(): Result<Unit> {
        return try {
            val user = firebaseAuth.currentUser
                ?: return Result.failure(IllegalStateException("Nenhum usuário logado."))

            user.sendEmailVerification().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}