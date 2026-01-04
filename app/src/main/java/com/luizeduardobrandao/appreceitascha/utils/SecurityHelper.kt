package com.luizeduardobrandao.appreceitascha.utils

import android.content.Context
import com.scottyab.rootbeer.RootBeer

/**
 * Helper de segurança do aplicativo.
 * Verifica se o dispositivo está em condições seguras para executar.
 */
object SecurityHelper {

    /**
     * Verifica se o dispositivo está seguro (sem root, não modificado).
     *
     * @param context Contexto da aplicação
     * @return true se o dispositivo é seguro, false se detectar root/modificações
     */
    fun isDeviceSecure(context: Context): Boolean {
        val rootBeer = RootBeer(context)
        return !rootBeer.isRooted
    }
}