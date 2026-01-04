package com.luizeduardobrandao.appreceitascha

import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.luizeduardobrandao.appreceitascha.databinding.ActivityMainBinding
import com.luizeduardobrandao.appreceitascha.utils.SecurityHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val mainViewModel: MainViewModel by viewModels()

    // NavController centralizado, reaproveitável (toolbar, etc.)
    private val navController: NavController by lazy {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navHostFragment.navController
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // ✅ VERIFICAÇÃO DE SEGURANÇA DO DISPOSITIVO
        if (!SecurityHelper.isDeviceSecure(this)) {
            AlertDialog.Builder(this)
                .setTitle("Dispositivo não seguro")
                .setMessage("Este app não pode ser executado em dispositivos com root ou modificados por questões de segurança.")
                .setCancelable(false)
                .setPositiveButton("Sair") { _, _ ->
                    finish()
                }
                .show()
            return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val bottomNav: BottomNavigationView = binding.bottomNav

        // Mantém o item do BottomNav sempre sincronizado com o destino atual
        navController.addOnDestinationChangedListener { _, destination, _ ->
            when (destination.id) {
                R.id.homeFragment -> {
                    bottomNav.menu.findItem(R.id.homeFragment).isChecked = true
                }

                R.id.categoriesFragment -> {
                    bottomNav.menu.findItem(R.id.categoriesFragment).isChecked = true
                }

                R.id.favoritesFragment -> {
                    bottomNav.menu.findItem(R.id.favoritesFragment).isChecked = true
                }

                R.id.loginFragment,
                R.id.registerFragment,
                R.id.resetPasswordFragment -> {
                    // Qualquer tela do fluxo de autenticação marca o item de login/perfil
                    bottomNav.menu.findItem(R.id.loginFragment).isChecked = true
                }
            }
        }

        // Observa mudanças no estado de autenticação (para trocar ícone/título de login/perfil)
        observeAuthState(bottomNav)

        // Controle TOTAL da navegação do BottomNav
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.homeFragment -> {
                    navController.navigateSingleTop(R.id.homeFragment)
                    true
                }

                R.id.categoriesFragment -> {
                    navController.navigateSingleTop(R.id.categoriesFragment)
                    true
                }

                R.id.favoritesFragment -> {
                    navController.navigateSingleTop(R.id.favoritesFragment)
                    true
                }

                R.id.loginFragment -> {
                    // Decide entre Login ou Perfil baseado no estado de autenticação
                    handleProfileOrLoginNavigation(navController)
                    true
                }

                else -> false
            }
        }

        // Edge-to-edge: aplica inset no TOP (conteúdo) e usa margin no BottomNav (não empurra a barra)
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            // Root: só topo/laterais
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)

            // BottomNav: desenha até o fim e protege ícones com padding
            binding.bottomNav.updatePadding(bottom = systemBars.bottom)

            insets
        }
    }

    override fun onResume() {
        super.onResume()
        // Atualiza o estado quando a Activity volta ao foco
        mainViewModel.refreshAuthState()
    }

    /**
     * Observa mudanças no estado de autenticação e atualiza o ícone do bottom navigation
     */
    private fun observeAuthState(bottomNav: BottomNavigationView) {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                mainViewModel.currentUser.collect { user ->
                    updateBottomNavIcon(bottomNav, user != null)
                }
            }
        }
    }

    /**
     * Atualiza o ícone do item de perfil/login no bottom navigation
     */
    private fun updateBottomNavIcon(bottomNav: BottomNavigationView, isLoggedIn: Boolean) {
        val menu = bottomNav.menu
        val profileItem = menu.findItem(R.id.loginFragment)

        if (isLoggedIn) {
            // Usuário logado - mostra ícone de perfil (edit)
            profileItem?.setIcon(R.drawable.ic_bottom_login_edit_24)
            profileItem?.title = getString(R.string.bottom_nav_profile)
        } else {
            // Usuário não logado - mostra ícone de login
            profileItem?.setIcon(R.drawable.ic_bottom_login_24)
            profileItem?.title = getString(R.string.bottom_nav_login)
        }
    }

    /**
     * Decide se navega para perfil (se logado) ou login (se não logado)
     */
    private fun handleProfileOrLoginNavigation(navController: NavController) {
        if (mainViewModel.isUserLoggedIn()) {
            // Usuário logado → vai para perfil (RegisterFragment em modo edição)
            val bundle = Bundle().apply {
                putBoolean("isEditMode", true)
            }
            navController.navigateSingleTop(R.id.registerFragment, bundle)
        } else {
            // Usuário não logado → vai para tela de login
            navController.navigateSingleTop(R.id.loginFragment)
        }
    }

    /**
     * Suporte a botão "Up" em toolbar (se você adicionar no futuro)
     */
    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp() || super.onSupportNavigateUp()
    }
}

/**
 * Extensão para navegar sempre em modo "singleTop" e limpar o stack
 * até o startDestination ao trocar de aba.
 *
 * Mantém o comportamento que já resolveu o bug:
 * - Trocar de aba: Home fica como base, destino da aba entra no topo.
 * - Back: volta para Home, depois fecha o app.
 */
private fun NavController.navigateSingleTop(
    destinationId: Int,
    args: Bundle? = null
) {
    // Evita trabalho desnecessário se já está no destino
    if (currentDestination?.id == destinationId) {
        return
    }

    val navOptions = NavOptions.Builder()
        .setLaunchSingleTop(true)
        // Garante que, ao trocar de aba, limpamos o que está acima do startDestination (Home)
        .setPopUpTo(graph.startDestinationId, false)
        .build()

    navigate(destinationId, args, navOptions)
}