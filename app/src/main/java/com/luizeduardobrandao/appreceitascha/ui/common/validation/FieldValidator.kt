package com.luizeduardobrandao.appreceitascha.ui.common.validation

import com.google.android.material.textfield.TextInputLayout

/**
 * Aplica as regras de [FieldValidationRules] nos TextInputLayouts,
 * convertendo o id de string + args em texto usando o Context do campo.
 */
class FieldValidator {

    fun validateNameField(
        til: TextInputLayout,
        value: String
    ): Boolean {
        val result = FieldValidationRules.validateName(value)
        applyResult(til, result)
        return result.isValid
    }

    fun validateEmailField(
        til: TextInputLayout,
        value: String
    ): Boolean {
        val result = FieldValidationRules.validateEmail(value)
        applyResult(til, result)
        return result.isValid
    }

    fun validatePhoneField(
        til: TextInputLayout,
        value: String
    ): Boolean {
        val result = FieldValidationRules.validatePhone(value)
        applyResult(til, result)
        return result.isValid
    }

    fun validatePasswordField(
        til: TextInputLayout,
        value: String
    ): Boolean {
        val result = FieldValidationRules.validatePassword(value)
        applyResult(til, result)
        return result.isValid
    }

    fun validateConfirmPasswordField(
        til: TextInputLayout,
        password: String,
        confirm: String
    ): Boolean {
        val result = FieldValidationRules.validateConfirmPassword(password, confirm)
        applyResult(til, result)
        return result.isValid
    }

    private fun applyResult(
        til: TextInputLayout,
        result: FieldValidationRules.ValidationResult
    ) {
        if (result.isValid || result.errorResId == null) {
            til.error = null
            til.isErrorEnabled = false
            return
        }

        val context = til.context
        val message = if (result.errorArgs != null) {
            context.getString(result.errorResId, *result.errorArgs)
        } else {
            context.getString(result.errorResId)
        }

        til.error = message
        til.isErrorEnabled = true
    }
}