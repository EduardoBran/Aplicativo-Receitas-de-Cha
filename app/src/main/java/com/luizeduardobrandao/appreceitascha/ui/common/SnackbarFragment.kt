package com.luizeduardobrandao.appreceitascha.ui.common

import android.graphics.Rect
import android.view.View
import android.view.ViewTreeObserver
import androidx.core.content.ContextCompat
import com.google.android.material.snackbar.Snackbar
import com.luizeduardobrandao.appreceitascha.R
import java.lang.ref.WeakReference

object SnackbarFragment {

    // ✅ Armazena listeners ativos para poder cancelá-los
    private val activeListeners = mutableSetOf<ViewTreeObserver.OnGlobalLayoutListener>()

    fun showSuccess(view: View, message: String) {
        show(view, message, SnackbarType.SUCCESS)
    }

    fun showError(view: View, message: String) {
        show(view, message, SnackbarType.ERROR)
    }

    fun showWarning(view: View, message: String) {
        show(view, message, SnackbarType.WARNING)
    }

    /**
     * Cancela todos os listeners pendentes.
     * Útil quando um Fragment está sendo destruído.
     */
    fun cancelPendingSnackbars(view: View) {
        val rootView = view.rootView
        activeListeners.forEach { listener ->
            try {
                rootView.viewTreeObserver.removeOnGlobalLayoutListener(listener)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        activeListeners.clear()
    }

    private fun show(view: View, message: String, type: SnackbarType) {
        // ✅ VALIDAÇÃO 1: Verifica se a view ainda está anexada
        if (!view.isAttachedToWindow) {
            return
        }

        if (isKeyboardVisible(view)) {
            waitForKeyboardToHide(view) {
                // ✅ VALIDAÇÃO 2: Verifica novamente antes de mostrar
                if (view.isAttachedToWindow) {
                    showSnackbar(view, message, type)
                }
            }
        } else {
            showSnackbar(view, message, type)
        }
    }

    private fun showSnackbar(view: View, message: String, type: SnackbarType) {
        // ✅ VALIDAÇÃO 3: Última verificação antes de criar o Snackbar
        if (!view.isAttachedToWindow) {
            return
        }

        try {
            val snackbar = Snackbar.make(view, message, Snackbar.LENGTH_LONG)

            val backgroundColor = when (type) {
                SnackbarType.SUCCESS -> ContextCompat.getColor(
                    view.context,
                    R.color.color_primary_base
                )

                SnackbarType.ERROR -> ContextCompat.getColor(view.context, R.color.text_error)
                SnackbarType.WARNING -> ContextCompat.getColor(view.context, R.color.warning_color)
            }

            snackbar.view.setBackgroundColor(backgroundColor)
            snackbar.setTextColor(ContextCompat.getColor(view.context, R.color.white))
            snackbar.show()
        } catch (e: IllegalArgumentException) {
            // ✅ PROTEÇÃO: Se mesmo assim falhar, não crasha o app
            e.printStackTrace()
        }
    }

    private fun waitForKeyboardToHide(view: View, onKeyboardHidden: () -> Unit) {
        // ✅ VALIDAÇÃO 4: Não adiciona listener se view não está anexada
        if (!view.isAttachedToWindow) {
            return
        }

        // ✅ Usa WeakReference para evitar memory leak
        val weakView = WeakReference(view)
        val rootView = view.rootView

        val listener = object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                val actualView = weakView.get()

                // ✅ VALIDAÇÃO 5: Verifica se view ainda existe e está anexada
                if (actualView == null || !actualView.isAttachedToWindow) {
                    // Remove o listener e limpa da lista
                    rootView.viewTreeObserver.removeOnGlobalLayoutListener(this)
                    activeListeners.remove(this)
                    return
                }

                if (!isKeyboardVisible(actualView)) {
                    // Remove o listener ANTES de executar o callback
                    rootView.viewTreeObserver.removeOnGlobalLayoutListener(this)
                    activeListeners.remove(this)
                    onKeyboardHidden()
                }
            }
        }

        // ✅ Adiciona à lista de listeners ativos
        activeListeners.add(listener)
        rootView.viewTreeObserver.addOnGlobalLayoutListener(listener)
    }

    private fun isKeyboardVisible(view: View): Boolean {
        val rect = Rect()
        view.getWindowVisibleDisplayFrame(rect)
        val screenHeight = view.rootView.height
        val keypadHeight = screenHeight - rect.bottom
        return keypadHeight > screenHeight * 0.15
    }

    private enum class SnackbarType {
        SUCCESS, ERROR, WARNING
    }
}