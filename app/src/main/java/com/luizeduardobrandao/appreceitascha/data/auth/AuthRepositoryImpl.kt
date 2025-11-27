package com.luizeduardobrandao.appreceitascha.data.auth

import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.FirebaseDatabase
import com.luizeduardobrandao.appreceitascha.domain.auth.AuthRepository
import com.luizeduardobrandao.appreceitascha.domain.auth.User
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementação concreta do [AuthRepository] utilizando [FirebaseAuthDataSource]
 * e [FirebaseDatabase] para salvar o perfil do usuário.
 *
 * - FirebaseAuth: cuida de login/cadastro (e-mail + senha).
 * - Realtime Database: guarda o perfil (nome, e-mail, telefone, etc.) em /users/{uid}.
 */
@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val authDataSource: FirebaseAuthDataSource,
    private val database: FirebaseDatabase
) : AuthRepository {

    /**
     * Login delegando ao [FirebaseAuthDataSource] e mapeando para [User].
     */
    override suspend fun login(email: String, password: String): Result<User> {
        return authDataSource
            .signIn(email, password)
            .map { firebaseUser -> firebaseUser.toDomainUser() }
    }

    /**
     * Cadastro de usuário:
     * 1) Cria o usuário no FirebaseAuth (e-mail/senha)
     * 2) Atualiza o displayName (já feito no DataSource)
     * 3) Salva o perfil no Realtime Database em /users/{uid}
     *
     * @param name Nome informado na tela de cadastro.
     * @param email E-mail do usuário.
     * @param password Senha.
     * @param phone Telefone opcional (pode ser null).
     */
    override suspend fun register(
        name: String,
        email: String,
        password: String,
        phone: String?
    ): Result<User> {
        // 1) Tenta criar o usuário no FirebaseAuth
        val signUpResult = authDataSource.signUp(name, email, password, phone)

        if (signUpResult.isFailure) {
            // Propaga o erro de cadastro (e-mail em uso, senha fraca, etc.)
            return Result.failure(
                signUpResult.exceptionOrNull()
                    ?: Exception("Erro desconhecido ao cadastrar usuário.")
            )
        }

        val firebaseUser = signUpResult.getOrThrow()

        return try {
            // 2) Converte para o modelo de domínio User
            val user = firebaseUser.toDomainUser(
                explicitName = name,
                explicitPhone = phone
            )

            // 3) Monta referência /users/{uid} no Realtime Database
            val userRef = database.getReference("users")
                .child(user.uid)

            // 4) Mapa com os dados que queremos gravar no banco
            //    Telefone é opcional: se for null, salvamos como string vazia.
            val userData = mapOf(
                "uid" to user.uid,
                "name" to (user.name ?: ""),
                "email" to user.email,
                "phone" to (user.phone ?: ""),
                "emailVerified" to user.isEmailVerified
            )

            // 5) Salva o perfil no Realtime Database (operação assíncrona)
            userRef.setValue(userData).await()

            // Retorna sucesso com o User de domínio
            Result.success(user)
        } catch (e: Exception) {
            // Se der erro ao salvar no DB, retornamos falha
            Result.failure(e)
        }
    }

    /**
     * Encapsula o envio de e-mail de reset de senha.
     */
    override suspend fun sendPasswordReset(email: String): Result<Unit> {
        return authDataSource.sendPasswordResetEmail(email)
    }

    /**
     * Lê o usuário logado a partir do FirebaseAuth e converte para [User].
     */
    override fun getCurrentUser(): User? {
        val firebaseUser = authDataSource.getCurrentFirebaseUser()
        return firebaseUser?.toDomainUser()
    }

    /**
     * Efetua logout delegando ao DataSource.
     */
    override fun logout() {
        authDataSource.logout()
    }
}

/**
 * Função de extensão para converter um [FirebaseUser] em [User] (modelo de domínio).
 *
 * @param explicitName Nome informado explicitamente no fluxo (por exemplo, na tela de cadastro).
 * @param explicitPhone Telefone lido do formulário.
 * Se null, tentamos usar o phoneNumber do Firebase (só é preenchido se o provedor de login for telefone/SMS).
 */
private fun FirebaseUser.toDomainUser(
    explicitName: String? = null,
    explicitPhone: String? = null
): User {
    return User(
        uid = uid,
        name = explicitName ?: displayName,
        email = email.orEmpty(),
        phone = explicitPhone ?: phoneNumber,
        isEmailVerified = isEmailVerified
    )
}