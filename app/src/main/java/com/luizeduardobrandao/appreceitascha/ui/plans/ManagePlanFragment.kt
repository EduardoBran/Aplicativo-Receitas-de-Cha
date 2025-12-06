package com.luizeduardobrandao.appreceitascha.ui.plans

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.luizeduardobrandao.appreceitascha.R
import com.luizeduardobrandao.appreceitascha.databinding.FragmentManagePlanBinding
import com.luizeduardobrandao.appreceitascha.domain.auth.AuthState
import com.luizeduardobrandao.appreceitascha.domain.auth.PlanState
import com.luizeduardobrandao.appreceitascha.domain.auth.PlanType
import com.luizeduardobrandao.appreceitascha.ui.common.SnackbarFragment
import com.luizeduardobrandao.appreceitascha.ui.home.HomeUiState
import com.luizeduardobrandao.appreceitascha.ui.home.HomeViewModel
import dagger.hilt.android.AndroidEntryPoint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch
import androidx.core.net.toUri

@AndroidEntryPoint
class ManagePlanFragment : Fragment() {

    private var _binding: FragmentManagePlanBinding? = null
    private val binding get() = _binding!!

    // Reaproveita o mesmo HomeViewModel para ler sessionState + userPlan
    private val homeViewModel: HomeViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentManagePlanBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupClickListeners()
        observeHomeState()

        // Garante que o estado esteja atualizado ao abrir a tela
        homeViewModel.refreshSession()
    }

    private fun setupClickListeners() {
        binding.btnManageGoToLogin.setOnClickListener {
            findNavController().navigate(R.id.loginFragment)
        }

        binding.btnManageSeePlans.setOnClickListener {
            findNavController().navigate(R.id.plansFragment)
        }

        binding.btnManageCancelPlan.setOnClickListener {
            openPlaySubscriptions()
        }
    }

    private fun observeHomeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                homeViewModel.uiState.collect { uiState ->
                    renderUi(uiState)
                }
            }
        }
    }

    private fun renderUi(uiState: HomeUiState) {
        val session = uiState.sessionState
        val authState = session?.authState ?: AuthState.NAO_LOGADO
        val planState = session?.planState ?: PlanState.SEM_PLANO
        val userPlan = uiState.userPlan

        // Estados principais
        val isLogged = authState == AuthState.LOGADO
        val hasPlan = planState == PlanState.COM_PLANO && userPlan != null

        // Layouts visíveis conforme o estado
        binding.layoutManageNotLogged.isVisible = !isLogged
        binding.layoutManageNoPlan.isVisible = isLogged && !hasPlan
        binding.cardManagePlan.isVisible = isLogged && hasPlan

        if (!isLogged || !hasPlan || userPlan == null) {
            return
        }

        // Nome do plano
        val planName = when (userPlan.planType) {
            PlanType.PLAN_3M -> getString(R.string.home_plan_3m)
            PlanType.PLAN_6M -> getString(R.string.home_plan_6m)
            PlanType.PLAN_12M -> getString(R.string.home_plan_12m)
            PlanType.PLAN_LIFE -> getString(R.string.home_plan_life)
            PlanType.NONE -> getString(R.string.home_plan_unknown)
        }
        binding.tvManagePlanName.text = planName

        // Expiração / vitalício
        val expiresText = when {
            userPlan.isLifetime -> {
                binding.tvManagePlanNote.isVisible = true
                getString(R.string.manage_plan_lifetime_label)
            }

            userPlan.expiresAtMillis != null -> {
                binding.tvManagePlanNote.isVisible = false
                val formatted = formatDate(userPlan.expiresAtMillis)
                getString(R.string.manage_plan_expires_label, formatted)
            }

            else -> {
                binding.tvManagePlanNote.isVisible = false
                ""
            }
        }
        binding.tvManagePlanExpires.text = expiresText
    }

    private fun formatDate(timestampMillis: Long): String {
        val date = Date(timestampMillis)
        val formatter = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))
        return formatter.format(date)
    }

    /**
     * Abre a tela de assinaturas da Google Play para o usuário gerenciar/cancelar.
     * (Cancelamento REAL da cobrança sempre é feito pela Google Play.)
     */
    private fun openPlaySubscriptions() {
        val uri = "https://play.google.com/store/account/subscriptions".toUri()
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            // Tenta abrir diretamente na Play Store
            setPackage("com.android.vending")
        }

        try {
            val pm = requireContext().packageManager
            if (intent.resolveActivity(pm) != null) {
                startActivity(intent)
            } else {
                // Fallback: abre em qualquer navegador
                startActivity(Intent(Intent.ACTION_VIEW, uri))
            }
        } catch (e: Exception) {
            SnackbarFragment.showError(
                binding.root,
                getString(R.string.manage_plan_play_unavailable)
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}