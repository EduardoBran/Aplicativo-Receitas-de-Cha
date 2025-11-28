package com.luizeduardobrandao.appreceitascha.ui.common.view

import android.content.Context
import androidx.appcompat.content.res.AppCompatResources
import com.google.android.material.textfield.TextInputEditText
import com.luizeduardobrandao.appreceitascha.R

/**
 * Gerencia a exibição do ícone de campo obrigatório ao lado direito
 * dos TextInputEditText.
 */
class RequiredIconManager(private val context: Context) {

    /**
     * Define se o ícone de "obrigatório" deve aparecer no EditText.
     */
    fun setRequiredIconVisible(
        editText: TextInputEditText,
        visible: Boolean
    ) {
        val drawables = editText.compoundDrawablesRelative
        val start = drawables[0]
        val top = drawables[1]
        val end = if (visible) {
            AppCompatResources.getDrawable(context, R.drawable.ic_field_required_24)
        } else {
            null
        }
        val bottom = drawables[3]

        editText.setCompoundDrawablesRelativeWithIntrinsicBounds(
            start,
            top,
            end,
            bottom
        )
    }
}