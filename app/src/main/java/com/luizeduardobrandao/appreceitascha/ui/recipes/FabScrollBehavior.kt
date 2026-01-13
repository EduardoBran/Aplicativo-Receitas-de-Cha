package com.luizeduardobrandao.appreceitascha.ui.recipes

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.floatingactionbutton.FloatingActionButton

class FabScrollBehavior(context: Context, attrs: AttributeSet) :
    CoordinatorLayout.Behavior<FloatingActionButton>(context, attrs) {

    override fun layoutDependsOn(
        parent: CoordinatorLayout,
        child: FloatingActionButton,
        dependency: View
    ): Boolean {
        return dependency is AppBarLayout
    }

    override fun onDependentViewChanged(
        parent: CoordinatorLayout,
        child: FloatingActionButton,
        dependency: View
    ): Boolean {
        if (dependency is AppBarLayout) {
            // Posiciona o FAB logo abaixo do AppBarLayout (totalmente fora)
            val appBarBottom = dependency.bottom + dependency.translationY
            child.y = appBarBottom  // Removido o "- (child.height / 2f)"

            // Posiciona horizontalmente no lado direito, respeitando a margem
            val layoutParams = child.layoutParams as? CoordinatorLayout.LayoutParams
            val marginEnd = layoutParams?.marginEnd ?: 0
            child.x = parent.width - child.width - marginEnd.toFloat()

            return true
        }
        return false
    }
}