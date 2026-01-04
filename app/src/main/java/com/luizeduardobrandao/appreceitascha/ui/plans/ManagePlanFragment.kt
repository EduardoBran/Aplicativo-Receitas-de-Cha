package com.luizeduardobrandao.appreceitascha.ui.plans

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.luizeduardobrandao.appreceitascha.R
import com.luizeduardobrandao.appreceitascha.databinding.FragmentManagePlanBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ManagePlanFragment : Fragment(R.layout.fragment_manage_plan) {

    private var _binding: FragmentManagePlanBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentManagePlanBinding.bind(view)

        setupListeners()
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }

        binding.btnSupport.setOnClickListener {
            // Abre cliente de email
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:")
                putExtra(Intent.EXTRA_EMAIL, arrayOf("eduardo.desenvolvedor.apps@gmail.com"))
                putExtra(Intent.EXTRA_SUBJECT, "Suporte - App Nature Chá")
            }
            startActivity(Intent.createChooser(intent, "Enviar email"))
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}