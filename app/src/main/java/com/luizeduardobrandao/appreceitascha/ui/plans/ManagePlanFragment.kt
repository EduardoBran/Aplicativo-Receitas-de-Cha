package com.luizeduardobrandao.appreceitascha.ui.plans

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.luizeduardobrandao.appreceitascha.R
import com.luizeduardobrandao.appreceitascha.databinding.FragmentManagePlanBinding
import dagger.hilt.android.AndroidEntryPoint
import androidx.core.net.toUri

/**
 * Fragment responsável pela tela de gerenciamento de plano.
 *
 * Permite ao usuário:
 * - Voltar para a tela anterior
 * - Entrar em contato com o suporte via e-mail
 */
@AndroidEntryPoint
class ManagePlanFragment : Fragment(R.layout.fragment_manage_plan) {

    private var _binding: FragmentManagePlanBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentManagePlanBinding.bind(view)

        setupListeners()
    }

    /**
     * Configura listeners dos botões da tela.
     */
    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }

        binding.btnSupport.setOnClickListener {
            openSupportEmail()
        }
    }

    /**
     * Abre o cliente de e-mail padrão para enviar mensagem ao suporte.
     *
     * O assunto do e-mail é pré-preenchido para facilitar a identificação.
     */
    private fun openSupportEmail() {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = "mailto:".toUri()
            putExtra(Intent.EXTRA_EMAIL, arrayOf("eduardo.desenvolvedor.apps@gmail.com"))
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.email_subject_support))
        }
        startActivity(Intent.createChooser(intent, getString(R.string.email_chooser_title)))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}