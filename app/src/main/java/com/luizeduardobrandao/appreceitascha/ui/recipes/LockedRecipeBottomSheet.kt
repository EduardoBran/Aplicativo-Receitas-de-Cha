package com.luizeduardobrandao.appreceitascha.ui.recipes

import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.luizeduardobrandao.appreceitascha.MainViewModel
import com.luizeduardobrandao.appreceitascha.R
import com.luizeduardobrandao.appreceitascha.databinding.FragmentLockedRecipeBottomSheetBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class LockedRecipeBottomSheet : BottomSheetDialogFragment() {

    private var _binding: FragmentLockedRecipeBottomSheetBinding? = null
    private val binding get() = _binding!!

    private val mainViewModel: MainViewModel by activityViewModels()

    private val args: LockedRecipeBottomSheetArgs by navArgs()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isCancelable = false
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.setOnShowListener { dialogInterface ->
            val bottomSheetDialog = dialogInterface as BottomSheetDialog
            val bottomSheet =
                bottomSheetDialog.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)

            bottomSheet?.let { sheet ->
                sheet.setBackgroundColor(Color.TRANSPARENT)
                val behavior = BottomSheetBehavior.from(sheet)
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                behavior.isDraggable = false
            }
        }
        dialog.setCanceledOnTouchOutside(false)
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLockedRecipeBottomSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // ✅ ATUALIZAÇÃO DE UI BASEADA NO ESTADO
        updateContentForUserState()

        setupListeners()
    }

    private fun updateContentForUserState() {
        val isUserLoggedIn = mainViewModel.isUserLoggedIn()
        val isFavoriteAction = args.isFavoriteAction

        if (isUserLoggedIn) {
            // Cenário: Usuário Logado (Free)
            if (isFavoriteAction) {
                // Veio do clique no coração
                binding.tvTitle.text = getString(R.string.locked_favorite_title_user)
                binding.tvDesc.text = getString(R.string.locked_favorite_desc_user)
            } else {
                // Veio do clique na receita bloqueada
                binding.tvTitle.text = getString(R.string.locked_sheet_title)
                binding.tvDesc.text = getString(R.string.locked_sheet_desc)
            }
            binding.btnOffer.text = getString(R.string.locked_sheet_btn_offer)

        } else {
            // Cenário: Visitante
            if (isFavoriteAction) {
                // Veio do clique no coração
                binding.tvTitle.text = getString(R.string.locked_favorite_title_guest)
                binding.tvDesc.text = getString(R.string.locked_favorite_desc_guest)
            } else {
                // Veio do clique na receita bloqueada
                binding.tvTitle.text = getString(R.string.locked_sheet_title_guest)
                binding.tvDesc.text = getString(R.string.locked_sheet_desc_guest)
            }
            binding.btnOffer.text = getString(R.string.locked_sheet_btn_guest)
        }
    }

    private fun setupListeners() {
        binding.btnClose.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.btnOffer.setOnClickListener {
            if (mainViewModel.isUserLoggedIn()) {
                // Cenário 2: Vai para Planos
                val action =
                    LockedRecipeBottomSheetDirections.actionLockedRecipeBottomSheetToPlansFragment()
                findNavController().navigate(action)
            } else {
                // Cenário 1: Vai para Login
                val action =
                    LockedRecipeBottomSheetDirections.actionLockedRecipeBottomSheetToLoginFragment()
                findNavController().navigate(action)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}