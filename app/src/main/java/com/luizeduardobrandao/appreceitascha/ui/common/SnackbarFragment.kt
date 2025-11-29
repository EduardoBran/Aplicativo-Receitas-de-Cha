package com.luizeduardobrandao.appreceitascha.ui.common

import android.view.Gravity
import android.view.View
import android.view.ViewTreeObserver
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.snackbar.Snackbar
import com.luizeduardobrandao.appreceitascha.R

/**
 * "SnackbarFragment" é um helper para exibir um retângulo pequeno
 * (Snackbar) com mensagens de erro ou sucesso por ~2 segundos.
 *
 * - Erro: fundo vermelho, texto branco.
 * - Sucesso: fundo verde, texto branco.
 *
 * O Snackbar só é exibido quando o teclado está oculto.
 */
object SnackbarFragment {

    fun showError(rootView: View, message: String) {
        show(rootView, message, isError = true)
    }

    fun showSuccess(rootView: View, message: String) {
        show(rootView, message, isError = false)
    }

    private fun show(rootView: View, message: String, isError: Boolean) {
        // Verifica se o teclado está visível
        if (isKeyboardVisible(rootView)) {
            // Aguarda o teclado fechar antes de mostrar o Snackbar
            waitForKeyboardToHide(rootView) {
                showSnackbar(rootView, message, isError)
            }
        } else {
            // Teclado já está oculto, mostra o Snackbar imediatamente
            showSnackbar(rootView, message, isError)
        }
    }

    private fun isKeyboardVisible(rootView: View): Boolean {
        val insets = ViewCompat.getRootWindowInsets(rootView)
        return insets?.isVisible(WindowInsetsCompat.Type.ime()) ?: false
    }

    private fun waitForKeyboardToHide(rootView: View, onKeyboardHidden: () -> Unit) {
        val listener = object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                if (!isKeyboardVisible(rootView)) {
                    // Teclado foi ocultado
                    rootView.viewTreeObserver.removeOnGlobalLayoutListener(this)
                    onKeyboardHidden()
                }
            }
        }
        rootView.viewTreeObserver.addOnGlobalLayoutListener(listener)
    }

    private fun showSnackbar(rootView: View, message: String, isError: Boolean) {
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

        // Centraliza horizontalmente o texto dentro do Snackbar
        val textView = snackbar.view.findViewById<TextView>(
            com.google.android.material.R.id.snackbar_text
        )
        textView.textAlignment = View.TEXT_ALIGNMENT_CENTER
        textView.gravity = Gravity.CENTER_HORIZONTAL

        snackbar.show()
    }
}