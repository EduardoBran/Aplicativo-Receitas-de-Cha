package com.luizeduardobrandao.appreceitascha.ui.common.extensions

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.widget.Button
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.luizeduardobrandao.appreceitascha.R

fun Context.showEmailVerificationDialog(email: String, onDismiss: () -> Unit = {}) {

    val inflater = LayoutInflater.from(this)
    val view = inflater.inflate(R.layout.dialog_email_verification, null)

    // Preenche o e-mail
    val tvUserEmail = view.findViewById<TextView>(R.id.tvUserEmail)
    tvUserEmail.text = email

    // Cria o dialog mas NÃO mostra ainda (precisamos da referência para o dismiss)
    val dialog = MaterialAlertDialogBuilder(this)
        .setView(view)
        .setCancelable(false) // Impede fechar tocando fora
        .setBackgroundInsetStart(16) // Ajuste para o card não colar na borda
        .setBackgroundInsetEnd(16)
        .create()

    // --- Configurações dos Botões ---

    // 1. Botão Principal: Abrir App de Email
    view.findViewById<Button>(R.id.btnOpenEmail).setOnClickListener {
        dialog.dismiss()
        onDismiss() // Callback importante para limpar o erro no ViewModel

        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_APP_EMAIL)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            startActivity(Intent.createChooser(intent, getString(R.string.dialog_chooser_title)))
        } catch (_: Exception) {
        }
    }

    // 2. Botão Secundário: Entendi
    view.findViewById<Button>(R.id.btnUnderstood).setOnClickListener {
        dialog.dismiss()
        onDismiss() // Callback importante para limpar o erro no ViewModel
    }

    dialog.show()
}