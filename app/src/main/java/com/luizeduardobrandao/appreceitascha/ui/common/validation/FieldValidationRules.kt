package com.luizeduardobrandao.appreceitascha.ui.common.validation

import androidx.annotation.StringRes
import com.luizeduardobrandao.appreceitascha.R
import android.util.Patterns

/**
 * Regras de validação de campos da camada de UI.
 *
 * NÃO retorna texto direto, e sim o id do recurso de string + args,
 * para que tudo fique centralizado em strings.xml.
 */
object FieldValidationRules {

    private const val MIN_NAME_LENGTH = 3
    private const val MIN_PASSWORD_LENGTH = 6

    data class ValidationResult(
        val isValid: Boolean,
        @StringRes val errorResId: Int? = null,
        val errorArgs: Array<Any>? = null
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as ValidationResult

            if (isValid != other.isValid) return false
            if (errorResId != other.errorResId) return false
            if (errorArgs != null) {
                if (other.errorArgs == null) return false
                if (!errorArgs.contentEquals(other.errorArgs)) return false
            } else if (other.errorArgs != null) return false

            return true
        }

        override fun hashCode(): Int {
            var result = isValid.hashCode()
            result = 31 * result + (errorResId ?: 0)
            result = 31 * result + (errorArgs?.contentHashCode() ?: 0)
            return result
        }
    }

    fun validateName(name: String): ValidationResult {
        val trimmed = name.trim()
        if (trimmed.isBlank()) {
            return ValidationResult(
                isValid = false,
                errorResId = R.string.error_name_required
            )
        }
        if (trimmed.length < MIN_NAME_LENGTH) {
            return ValidationResult(
                isValid = false,
                errorResId = R.string.error_name_min_length,
                errorArgs = arrayOf(MIN_NAME_LENGTH)
            )
        }
        return ValidationResult(true, null, null)
    }

    fun validateEmail(email: String): ValidationResult {
        val trimmed = email.trim()
        if (trimmed.isBlank()) {
            return ValidationResult(
                isValid = false,
                errorResId = R.string.error_email_required
            )
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(trimmed).matches()) {
            return ValidationResult(
                isValid = false,
                errorResId = R.string.error_email_invalid
            )
        }
        return ValidationResult(true, null, null)
    }

    /**
     * Telefone é opcional:
     * - Se em branco → válido.
     * - Se preenchido → formato precisa ser de telefone.
     */
    fun validatePhone(phone: String): ValidationResult {
        val trimmed = phone.trim()

        // Telefone é opcional
        if (trimmed.isBlank()) {
            return ValidationResult(true, null, null)
        }

        // Mantém apenas dígitos (remove "()", espaço, etc.)
        val digits = trimmed.filter { it.isDigit() }

        // Aceita SOMENTE 11 dígitos (ex.: 21998848525)
        val isValidBrazilianMobile = digits.length == 11

        return if (isValidBrazilianMobile) {
            ValidationResult(true, null, null)
        } else {
            ValidationResult(
                isValid = false,
                errorResId = R.string.error_phone_invalid
            )
        }
    }

    fun validatePassword(password: String): ValidationResult {
        if (password.isBlank()) {
            return ValidationResult(
                isValid = false,
                errorResId = R.string.error_password_required
            )
        }
        if (password.length < MIN_PASSWORD_LENGTH) {
            return ValidationResult(
                isValid = false,
                errorResId = R.string.error_password_min_length,
                errorArgs = arrayOf(MIN_PASSWORD_LENGTH)
            )
        }
        return ValidationResult(true, null, null)
    }

    fun validateConfirmPassword(password: String, confirm: String): ValidationResult {
        if (confirm.isBlank()) {
            return ValidationResult(
                isValid = false,
                errorResId = R.string.error_confirm_password_required
            )
        }
        if (confirm != password) {
            return ValidationResult(
                isValid = false,
                errorResId = R.string.error_confirm_password_mismatch
            )
        }
        return ValidationResult(true, null, null)
    }
}