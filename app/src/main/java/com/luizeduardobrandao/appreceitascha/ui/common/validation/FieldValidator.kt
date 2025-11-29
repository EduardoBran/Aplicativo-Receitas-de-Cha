package com.luizeduardobrandao.appreceitascha.ui.common.validation

import android.content.res.ColorStateList
import androidx.core.content.ContextCompat
import com.google.android.material.textfield.TextInputLayout
import com.luizeduardobrandao.appreceitascha.R

class FieldValidator {

    fun validateNameField(
        til: TextInputLayout,
        value: String
    ): Boolean {
        val result = FieldValidationRules.validateName(value)
        applyResult(til, result, value)
        return result.isValid
    }

    fun validateEmailField(
        til: TextInputLayout,
        value: String
    ): Boolean {
        val result = FieldValidationRules.validateEmail(value)
        applyResult(til, result, value)
        return result.isValid
    }

    fun validatePhoneField(
        til: TextInputLayout,
        value: String
    ): Boolean {
        val result = FieldValidationRules.validatePhone(value)
        applyResult(til, result, value)
        return result.isValid
    }

    fun validatePasswordField(
        til: TextInputLayout,
        value: String
    ): Boolean {
        val result = FieldValidationRules.validatePassword(value)
        applyResult(til, result, value)
        return result.isValid
    }

    fun validateConfirmPasswordField(
        til: TextInputLayout,
        password: String,
        confirm: String
    ): Boolean {
        val result = FieldValidationRules.validateConfirmPassword(password, confirm)
        applyResult(til, result, confirm)
        return result.isValid
    }

    private fun applyResult(
        til: TextInputLayout,
        result: FieldValidationRules.ValidationResult,
        currentValue: String
    ) {
        val context = til.context

        // Se campo está VAZIO, remove erro
        if (currentValue.isBlank()) {
            clearError(til)
            return
        }

        // Campo TEM conteúdo E é VÁLIDO
        if (result.isValid) {
            clearError(til)
            return
        }

        // Campo TEM conteúdo E é INVÁLIDO → borda vermelha
        val message = if (result.errorArgs != null) {
            result.errorResId?.let { context.getString(it, *result.errorArgs) }
        } else {
            result.errorResId?.let { context.getString(it) }
        }

        til.isErrorEnabled = false
        til.error = null
        til.tag = message

        // Cria ColorStateList vermelho que persiste em TODOS os estados
        val errorColor = ContextCompat.getColor(context, R.color.text_error)
        val errorColorStateList = createErrorColorStateList(errorColor)
        til.setBoxStrokeColorStateList(errorColorStateList)
    }

    private fun clearError(til: TextInputLayout) {
        val context = til.context

        til.isErrorEnabled = false
        til.error = null
        til.tag = null

        // Restaura ColorStateList padrão
        val normalColor = ContextCompat.getColor(context, R.color.edittext_stroke)
        val normalColorStateList = createNormalColorStateList(normalColor)
        til.setBoxStrokeColorStateList(normalColorStateList)
    }

    /**
     * Cria ColorStateList vermelho que persiste em TODOS os estados
     */
    private fun createErrorColorStateList(errorColor: Int): ColorStateList {
        val states = arrayOf(
            intArrayOf(android.R.attr.state_focused),   // focused
            intArrayOf(-android.R.attr.state_focused),  // unfocused
            intArrayOf(android.R.attr.state_hovered),   // hovered
            intArrayOf(-android.R.attr.state_enabled),  // disabled
            intArrayOf()                                 // default
        )
        val colors = intArrayOf(
            errorColor,  // focused = vermelho
            errorColor,  // unfocused = vermelho
            errorColor,  // hovered = vermelho
            errorColor,  // disabled = vermelho
            errorColor   // default = vermelho
        )
        return ColorStateList(states, colors)
    }

    /**
     * Cria ColorStateList normal (azul quando focused, cinza quando unfocused)
     */
    private fun createNormalColorStateList(normalColor: Int): ColorStateList {
        val states = arrayOf(
            intArrayOf(android.R.attr.state_focused),   // focused
            intArrayOf(-android.R.attr.state_focused),  // unfocused
            intArrayOf()                                 // default
        )
        val colors = intArrayOf(
            normalColor,  // focused
            normalColor,  // unfocused
            normalColor   // default
        )
        return ColorStateList(states, colors)
    }
}