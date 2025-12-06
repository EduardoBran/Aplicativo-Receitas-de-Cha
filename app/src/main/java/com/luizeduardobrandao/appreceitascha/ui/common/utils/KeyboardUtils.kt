package com.luizeduardobrandao.appreceitascha.ui.common.utils

import android.content.Context
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.fragment.app.Fragment

/**
 * Utilitário para gerenciar a visibilidade do teclado virtual.
 */
object KeyboardUtils {

    /**
     * Esconde o teclado virtual a partir de uma View.
     *
     * @param view A View que possui o foco ou a root view do layout.
     */
    private fun hideKeyboard(view: View) {
        val imm = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(view.windowToken, 0)
    }

    /**
     * Esconde o teclado virtual a partir de um Fragment.
     * Usa a view root do Fragment para fechar o teclado.
     *
     * @param fragment O Fragment atual.
     */
    fun hideKeyboard(fragment: Fragment) {
        fragment.view?.let { view ->
            hideKeyboard(view)
        }
    }

    /**
     * Mostra o teclado virtual para uma View específica.
     *
     * @param view A View que deve receber o foco e mostrar o teclado.
     */
    fun showKeyboard(view: View) {
        view.requestFocus()
        val imm = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
    }
}