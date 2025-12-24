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
import com.luizeduardobrandao.appreceitascha.ui.common.animation.LottieLoadingController
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

    // Controller para animação de loading com Lottie
    private lateinit var lottieController: LottieLoadingController

    // Controle de tempo mínimo da animação de loading
    private var isShowingLoadingOverlay: Boolean = false
    private var loadingStartTime: Long = 0L

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

        // Instancia o controlador de loading com Lottie centralizado
        lottieController = LottieLoadingController(
            overlay = binding.homeLoadingOverlay,
            lottieView = binding.lottieHome
        )

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
        // Se a view já foi destruída, não tenta renderizar nada
        if (_binding == null) return

        if (uiState.isLoading) {
            // Início de um novo ciclo de loading
            if (!isShowingLoadingOverlay) {
                isShowingLoadingOverlay = true
                loadingStartTime = System.currentTimeMillis()
                lottieController.showLoading()
            }

            // Enquanto estiver carregando, nenhum card deve aparecer
            hideAllCards()
            return
        }

        // Aqui, uiState.isLoading == false.
        // Garante que o Lottie fique visível por pelo menos MIN_HOME_LOADING_LOTTIE_MS.
        if (isShowingLoadingOverlay) {
            val elapsed = System.currentTimeMillis() - loadingStartTime
            if (elapsed < MIN_HOME_LOADING_LOTTIE_MS) {
                // Ainda não atingiu o tempo mínimo: mantém overlay e cards ocultos.
                hideAllCards()

                val delay = MIN_HOME_LOADING_LOTTIE_MS - elapsed

                binding.homeRoot.postDelayed({
                    if (!isAdded) return@postDelayed
                    if (_binding == null) return@postDelayed
                    if (!isShowingLoadingOverlay) return@postDelayed

                    isShowingLoadingOverlay = false
                    lottieController.hide()
                    renderNonLoadingUi(uiState)
                }, delay)

                return
            } else {
                // Já passou do tempo mínimo: pode esconder overlay imediatamente.
                isShowingLoadingOverlay = false
                lottieController.hide()
            }
        }

        // Não está mais carregando e o overlay já foi tratado: renderiza os cards normalmente.
        renderNonLoadingUi(uiState)
    }

    private fun hideAllCards() {
        val binding = _binding ?: return
        binding.groupGuest.isVisible = false
        binding.groupFree.isVisible = false
        binding.groupPremium.isVisible = false
    }

    private fun renderNonLoadingUi(uiState: HomeUiState) {
        val binding = _binding ?: return

        val session = uiState.sessionState
        val authState = session?.authState ?: AuthState.NAO_LOGADO
        val planState = session?.planState ?: PlanState.SEM_PLANO

        val isLogged = authState == AuthState.LOGADO
        val hasPlan = planState == PlanState.COM_PLANO

        // Visibilidade dos cards (somente após concluir o carregamento)
        binding.groupGuest.isVisible = !isLogged
        binding.groupFree.isVisible = isLogged && !hasPlan
        binding.groupPremium.isVisible = isLogged && hasPlan

        // Quando COM_PLANO, preenche informações do plano no card 3
        if (hasPlan) {
            val userPlan = uiState.userPlan

            val planName = when (userPlan?.planType) {
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

        uiState.errorMessage?.let {
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
            // Agora abre a tela de GERENCIAR PLANO (plano atual + botão de cancelar)
            findNavController().navigate(R.id.managePlanFragment)
        }
    }

    private fun formatDate(timestampMillis: Long): String {
        val date = Date(timestampMillis)
        val formatter = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))
        return formatter.format(date)
    }

    override fun onDestroyView() {
        if (::lottieController.isInitialized) {
            lottieController.clear()
        }
        _binding = null
        super.onDestroyView()
    }

    companion object {
        /** Tempo mínimo em ms que o Lottie deve permanecer visível na Home. */
        private const val MIN_HOME_LOADING_LOTTIE_MS: Long = 1000L
    }
}