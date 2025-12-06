package com.luizeduardobrandao.appreceitascha.ui.common.animation

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.view.View
import androidx.core.view.isGone
import androidx.core.view.isVisible
import com.airbnb.lottie.LottieAnimationView
import com.airbnb.lottie.LottieDrawable

/**
 * Controlador reutilizável para animações de loading com Lottie + overlay.
 *
 * Ideia principal:
 *  - showLoading(): mostra o overlay, coloca o Lottie em loop infinito (modo "carregando").
 *  - hide(): esconde o overlay, cancela a animação (usado em Idle / Erro).
 *  - playFinalAnimationThen(onFinished): tira do loop e deixa a animação
 *    seguir até o final do ciclo atual, só então:
 *       - faz fade-out do overlay
 *       - chama o callback [onFinished] (ex.: navegar de tela)
 */
class LottieLoadingController(
    private val overlay: View,
    private val lottieView: LottieAnimationView
) {

    /** Flag para garantir que o callback final seja chamado apenas uma vez por ciclo. */
    private var didRunFinalAction: Boolean = false

    /** Listener da animação final, para poder remover com segurança. */
    private var finalAnimListener: AnimatorListenerAdapter? = null

    /**
     * Coloca o overlay visível e inicia o Lottie em loop infinito,
     * no modo "carregando".
     */
    fun showLoading() {
        didRunFinalAction = false
        overlay.alpha = 1f
        overlay.isVisible = true

        // Evita listeners acumulados de outros ciclos
        lottieView.removeAllAnimatorListeners()

        lottieView.repeatCount = ValueAnimator.INFINITE
        lottieView.repeatMode = LottieDrawable.RESTART

        if (!lottieView.isAnimating) {
            lottieView.playAnimation()
        }
    }

    /**
     * Esconde o overlay e cancela qualquer animação em andamento.
     *
     * Use em estados Idle / Erro / Cancelamento.
     */
    fun hide() {
        overlay.isGone = true
        overlay.alpha = 1f
        lottieView.cancelAnimation()
        didRunFinalAction = false
    }

    /**
     * Usa o frame atual da animação para tocar até o fim (1f), sem "pular"
     * para o começo, e ao terminar:
     *  - Faz fade-out do overlay.
     *  - Chama [onFinished].
     *
     * Ideal para ser chamado quando o login/cadastro/etc. tiver sucesso.
     */
    fun playFinalAnimationThen(
        overlayFadeDurationMillis: Long = 180L,
        rewindIfCloseToEndProgress: Float = 0.95f,
        rewindTargetProgress: Float = 0.75f,
        onFinished: () -> Unit
    ) {
        // Evita navegações/ações duplicadas
        if (didRunFinalAction) return

        overlay.isVisible = true

        // Remove listeners antigos para não acumular callbacks
        lottieView.removeAllAnimatorListeners()

        // Sai do loop infinito e deixa tocar apenas até o fim
        lottieView.repeatCount = 0
        lottieView.repeatMode = LottieDrawable.RESTART

        // Captura o progresso atual (0f..1f)
        val currentProgress = lottieView.progress.coerceIn(0f, 1f)

        // Se já estiver muito perto do fim, volta um pouco para dar tempo de "ver" o final
        val startProgress = if (currentProgress >= rewindIfCloseToEndProgress) {
            rewindTargetProgress
        } else {
            currentProgress
        }

        try {
            lottieView.setMinAndMaxProgress(startProgress, 1f)
        } catch (_: Throwable) {
            // Compatibilidade com versões mais antigas do Lottie
            try {
                lottieView.setMinProgress(startProgress)
                lottieView.setMaxProgress(1f)
            } catch (_: Throwable) {
                // Se não suportar, simplesmente ignora o range,
                // a animação seguirá seu fluxo normal.
            }
        }

        finalAnimListener = object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                if (didRunFinalAction) return
                didRunFinalAction = true

                // Reseta range para uso futuro (novo ciclo de loading)
                try {
                    lottieView.setMinAndMaxProgress(0f, 1f)
                } catch (_: Throwable) {
                    try {
                        lottieView.setMinProgress(0f)
                        lottieView.setMaxProgress(1f)
                    } catch (_: Throwable) {
                    }
                }

                // Fade-out suave do overlay antes da ação final (ex.: navegar)
                overlay.animate()
                    .alpha(0f)
                    .setDuration(overlayFadeDurationMillis)
                    .withEndAction {
                        overlay.isGone = true
                        overlay.alpha = 1f
                        onFinished()
                    }
                    .start()
            }
        }

        lottieView.addAnimatorListener(finalAnimListener)

        // Se por acaso não estiver animando, garante que vai tocar o trecho final
        if (!lottieView.isAnimating) {
            lottieView.playAnimation()
        }
    }

    /**
     * Deve ser chamado em onDestroyView() do Fragment para evitar leaks.
     *
     * Não altera visibilidade nem estado de animação, apenas remove listeners
     * e reseta flags internas.
     */
    fun clear() {
        lottieView.removeAllAnimatorListeners()
        finalAnimListener = null
        didRunFinalAction = false
    }
}