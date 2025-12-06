package com.luizeduardobrandao.appreceitascha.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.luizeduardobrandao.appreceitascha.R
import com.luizeduardobrandao.appreceitascha.data.local.SessionManager
import com.luizeduardobrandao.appreceitascha.databinding.FragmentHomeBinding
import com.luizeduardobrandao.appreceitascha.domain.auth.AuthRepository
import com.luizeduardobrandao.appreceitascha.domain.auth.AuthState
import com.luizeduardobrandao.appreceitascha.domain.auth.PlanState
import com.luizeduardobrandao.appreceitascha.domain.auth.PlanType
import com.luizeduardobrandao.appreceitascha.ui.common.SnackbarFragment
import dagger.hilt.android.AndroidEntryPoint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    // ViewModel da Home (ETAPA 12)
    private val homeViewModel: HomeViewModel by viewModels()

    // Ainda usamos o AuthRepository aqui somente para a mensagem de boas-vindas e logout.
    @Inject
    lateinit var authRepository: AuthRepository

    @Inject
    lateinit var sessionManager: SessionManager

    private val args: HomeFragmentArgs by lazy {
        HomeFragmentArgs.fromBundle(requireArguments())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupWelcomeMessage()
        setupClickListeners()
        observeUiState()
    }

    /**
     * Exibe o snackbar de boas-vindas com o nome, usando arg ou usuário atual.
     */
    private fun setupWelcomeMessage() {
        // Verifica se já mostrou boas-vindas nesta sessão
        if (!sessionManager.shouldShowWelcome()) {
            return
        }

        val userNameFromArgs = args.userName
        val currentUser = authRepository.getCurrentUser()

        val nameToUse = when {
            !userNameFromArgs.isNullOrBlank() -> userNameFromArgs
            currentUser?.name?.isNotBlank() == true -> currentUser.name
            else -> null
        }

        if (!nameToUse.isNullOrBlank()) {
            SnackbarFragment.showSuccess(
                binding.root,
                getString(R.string.snackbar_success_welcome, nameToUse)
            )
            // Marca como exibido
            sessionManager.markWelcomeAsShown()
        }
    }

    /**
     * Observa o HomeUiState e mostra/oculta os cards de acordo com:
     * - NAO_LOGADO + SEM_PLANO → Card 1 (guest)
     * - LOGADO + SEM_PLANO     → Card 2 (free)
     * - LOGADO + COM_PLANO     → Card 3 (premium)
     */
    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                homeViewModel.uiState.collect { uiState ->
                    renderUi(uiState)
                }
            }
        }
    }

    private fun renderUi(uiState: HomeUiState) {
        binding.progressHome.isVisible = uiState.isLoading

        val session = uiState.sessionState
        val authState = session?.authState ?: AuthState.NAO_LOGADO
        val planState = session?.planState ?: PlanState.SEM_PLANO

        val isLogged = authState == AuthState.LOGADO
        val hasPlan = planState == PlanState.COM_PLANO

        // Visibilidade dos cards
        binding.groupGuest.isVisible = !isLogged
        binding.groupFree.isVisible = isLogged && !hasPlan
        binding.groupPremium.isVisible = isLogged && hasPlan

        // Quando COM_PLANO, preenche informações do plano no card 3
        if (hasPlan) {
            val userPlan = uiState.userPlan

            val planName = when (userPlan?.planType) {
                PlanType.PLAN_3M -> getString(R.string.home_plan_3m)
                PlanType.PLAN_6M -> getString(R.string.home_plan_6m)
                PlanType.PLAN_12M -> getString(R.string.home_plan_12m)
                PlanType.PLAN_LIFE -> getString(R.string.home_plan_life)
                PlanType.NONE, null -> getString(R.string.home_plan_unknown)
            }

            binding.tvPremiumPlanName.text =
                getString(R.string.home_premium_plan_label, planName)

            val expiresText = when {
                userPlan == null -> ""
                userPlan.isLifetime -> getString(R.string.home_plan_no_expiration)
                userPlan.expiresAtMillis != null -> {
                    val formattedDate = formatDate(userPlan.expiresAtMillis)
                    getString(R.string.home_plan_expires_at, formattedDate)
                }

                else -> ""
            }
            binding.tvPremiumPlanExpires.text = expiresText
        }

        // Se quiser exibir erro genérico no futuro:
        uiState.errorMessage?.let {
            // Evita spam de snackbar em cada recomposição — aqui você pode depois
            // trocar para um Event. Por enquanto, deixo simples:
            SnackbarFragment.showError(binding.root, it)
        }
    }

    private fun setupClickListeners() {
        // CARD 1 - Visitante
        binding.btnGuestPrimary.setOnClickListener {
            // "Fazer login / Cadastre-se"
            findNavController().navigate(R.id.loginFragment)
        }
        binding.btnGuestSeeRecipes.setOnClickListener {
            findNavController().navigate(R.id.categoriesFragment)
        }

        // CARD 2 - Logado + SEM_PLANO
        binding.btnFreeBuyPlan.setOnClickListener {
            // Vai para tela de planos
            findNavController().navigate(R.id.plansFragment)
        }
        binding.btnFreeSeeRecipes.setOnClickListener {
            findNavController().navigate(R.id.categoriesFragment)
        }

        // CARD 3 - Logado + COM_PLANO
        binding.btnPremiumSeeRecipes.setOnClickListener {
            findNavController().navigate(R.id.categoriesFragment)
        }
        binding.btnPremiumPlanDetails.setOnClickListener {
            findNavController().navigate(R.id.plansFragment)
        }
    }

    private fun formatDate(timestampMillis: Long): String {
        val date = Date(timestampMillis)
        val formatter = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))
        return formatter.format(date)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}