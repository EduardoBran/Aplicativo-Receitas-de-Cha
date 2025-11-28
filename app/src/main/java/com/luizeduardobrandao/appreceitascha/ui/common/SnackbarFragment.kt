package com.luizeduardobrandao.appreceitascha.ui.common

import android.view.View
import androidx.core.content.ContextCompat
import com.google.android.material.snackbar.Snackbar
import com.luizeduardobrandao.appreceitascha.R

/**
 * "SnackbarFragment" é um helper para exibir um retângulo pequeno
 * (Snackbar) com mensagens de erro ou sucesso por ~2 segundos.
 *
 * - Erro: fundo vermelho, texto branco.
 * - Sucesso: fundo verde, texto branco.
 *
 * Ele se adapta automaticamente à orientação (vertical/horizontal).
 */
object SnackbarFragment {

    fun showError(rootView: View, message: String) {
        show(rootView, message, isError = true)
    }

    fun showSuccess(rootView: View, message: String) {
        show(rootView, message, isError = false)
    }

    private fun show(rootView: View, message: String, isError: Boolean) {
        val snackbar = Snackbar.make(rootView, message, Snackbar.LENGTH_SHORT) // ~2s

        val backgroundColorRes = if (isError) {
            R.color.text_error
        } else {
            R.color.color_primary_base
        }

        snackbar.view.setBackgroundColor(
            ContextCompat.getColor(rootView.context, backgroundColorRes)
        )

        snackbar.setTextColor(
            ContextCompat.getColor(rootView.context, R.color.white)
        )

        snackbar.show()
    }
}