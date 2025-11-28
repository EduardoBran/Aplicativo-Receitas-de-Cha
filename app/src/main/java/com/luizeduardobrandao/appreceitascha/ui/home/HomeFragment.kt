package com.luizeduardobrandao.appreceitascha.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.luizeduardobrandao.appreceitascha.R
import com.luizeduardobrandao.appreceitascha.databinding.FragmentHomeBinding
import com.luizeduardobrandao.appreceitascha.domain.auth.AuthRepository
import com.luizeduardobrandao.appreceitascha.ui.common.SnackbarFragment
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    @Inject
    lateinit var authRepository: AuthRepository

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
        setupWelcomeMessage()
        setupListeners()
    }

    private fun setupWelcomeMessage() {
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
        }
    }

    private fun setupListeners() {
        binding.btnLogout.setOnClickListener {
            authRepository.logout()

            val navOptions = NavOptions.Builder()
                .setPopUpTo(R.id.homeFragment, true)
                .build()

            findNavController().navigate(R.id.loginFragment, null, navOptions)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}