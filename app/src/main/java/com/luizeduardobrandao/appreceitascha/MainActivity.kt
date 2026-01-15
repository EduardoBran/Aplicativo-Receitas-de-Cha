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

/**
 * Activity principal do app.
 *
 * Responsabilidades:
 * - Gerenciar navegação principal (BottomNavigationView + NavController)
 * - Observar estado de autenticação para trocar ícone Login/Perfil
 * - Aplicar edge-to-edge com WindowInsets
 * - Verificar segurança do dispositivo (root detection)
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val mainViewModel: MainViewModel by viewModels()

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
            showSecurityDialog()
            return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupBottomNavigation()
        setupEdgeToEdge()
    }

    /**
     * Exibe diálogo de segurança e encerra app se dispositivo for inseguro.
     */
    private fun showSecurityDialog() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.security_title))
            .setMessage(getString(R.string.security_message))
            .setCancelable(false)
            .setPositiveButton(getString(R.string.security_button_exit)) { _, _ ->
                finish()
            }
            .show()
    }

    /**
     * Configura BottomNavigationView e sincronização com NavController.
     */
    private fun setupBottomNavigation() {
        val bottomNav: BottomNavigationView = binding.bottomNav

        // Mantém item do BottomNav sincronizado com destino atual
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
                    // Fluxo de autenticação marca item de login/perfil
                    bottomNav.menu.findItem(R.id.loginFragment).isChecked = true
                }
            }
        }

        // Observa mudanças no estado de autenticação (troca ícone/título)
        observeAuthState(bottomNav)

        // Controle de navegação do BottomNav
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
                    handleProfileOrLoginNavigation(navController)
                    true
                }

                else -> false
            }
        }
    }

    /**
     * Configura edge-to-edge com WindowInsets.
     */
    private fun setupEdgeToEdge() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
            binding.bottomNav.updatePadding(bottom = systemBars.bottom)
            insets
        }
    }

    override fun onResume() {
        super.onResume()
        mainViewModel.refreshAuthState()
    }

    /**
     * Observa mudanças no estado de autenticação e atualiza ícone do bottom nav.
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
     * Atualiza ícone e título do item de perfil/login no bottom nav.
     */
    private fun updateBottomNavIcon(bottomNav: BottomNavigationView, isLoggedIn: Boolean) {
        val menu = bottomNav.menu
        val profileItem = menu.findItem(R.id.loginFragment)

        if (isLoggedIn) {
            profileItem?.setIcon(R.drawable.ic_bottom_login_edit_24)
            profileItem?.title = getString(R.string.bottom_nav_profile)
        } else {
            profileItem?.setIcon(R.drawable.ic_bottom_login_24)
            profileItem?.title = getString(R.string.bottom_nav_login)
        }
    }

    /**
     * Decide navegação para perfil (se logado) ou login (se não logado).
     */
    private fun handleProfileOrLoginNavigation(navController: NavController) {
        if (mainViewModel.isUserLoggedIn()) {
            val bundle = Bundle().apply {
                putBoolean("isEditMode", true)
            }
            navController.navigateSingleTop(R.id.registerFragment, bundle)
        } else {
            navController.navigateSingleTop(R.id.loginFragment)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp() || super.onSupportNavigateUp()
    }
}

/**
 * Extensão para navegar em modo singleTop com pop até startDestination.
 *
 * Comportamento:
 * - Trocar de aba: Home fica como base, destino da aba entra no topo
 * - Back: volta para Home, depois fecha o app
 */
private fun NavController.navigateSingleTop(
    destinationId: Int,
    args: Bundle? = null
) {
    if (currentDestination?.id == destinationId) {
        return
    }

    val navOptions = NavOptions.Builder()
        .setLaunchSingleTop(true)
        .setPopUpTo(graph.startDestinationId, false)
        .build()

    navigate(destinationId, args, navOptions)
}