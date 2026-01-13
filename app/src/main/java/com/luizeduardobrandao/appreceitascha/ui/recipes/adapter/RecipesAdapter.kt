package com.luizeduardobrandao.appreceitascha.ui.recipes.adapter

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.luizeduardobrandao.appreceitascha.R
import com.luizeduardobrandao.appreceitascha.databinding.ItemRecipeBinding
import com.luizeduardobrandao.appreceitascha.domain.recipes.Recipe

class RecipesAdapter(
    private val canOpenRecipe: (Recipe) -> Boolean,
    private val onRecipeClick: (Recipe) -> Unit
) : ListAdapter<Recipe, RecipesAdapter.RecipeViewHolder>(DIFF_CALLBACK) {

    // Controle para animação de entrada
    private var lastAnimatedPosition = -1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecipeViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemRecipeBinding.inflate(inflater, parent, false)
        return RecipeViewHolder(binding, canOpenRecipe, onRecipeClick)
    }

    override fun onBindViewHolder(holder: RecipeViewHolder, position: Int) {
        holder.bind(getItem(position))
        setEnterAnimation(holder.itemView, position)
    }

    /**
     * Aplica animação de entrada (Slide in from left).
     */
    private fun setEnterAnimation(viewToAnimate: View, position: Int) {
        if (position > lastAnimatedPosition) {
            val animation =
                AnimationUtils.loadAnimation(viewToAnimate.context, android.R.anim.slide_in_left)
            animation.duration = 350
            viewToAnimate.startAnimation(animation)
            lastAnimatedPosition = position
        }
    }

    override fun onViewDetachedFromWindow(holder: RecipeViewHolder) {
        holder.itemView.clearAnimation()
    }

    class RecipeViewHolder(
        private val binding: ItemRecipeBinding,
        private val canOpenRecipe: (Recipe) -> Boolean,
        private val onRecipeClick: (Recipe) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(recipe: Recipe) {
            binding.textRecipeTitle.text = recipe.title
            binding.textRecipeSubtitle.text = recipe.subtitle

            val isUnlocked = canOpenRecipe(recipe)

            if (isUnlocked) {
                // Receita liberada: Esconde o cadeado totalmente
                binding.imageRecipeLock.visibility = View.GONE
            } else {
                // Receita bloqueada: Mostra o cadeado fechado
                binding.imageRecipeLock.visibility = View.VISIBLE
                binding.imageRecipeLock.setImageResource(R.drawable.ic_recipe_lock_24)
                binding.imageRecipeLock.contentDescription =
                    binding.root.context.getString(R.string.recipe_lock_closed_content_description)
            }

            // Configura a animação de clique e o listener
            setupClickAnimation(binding.cardRecipe, recipe)
        }

        /**
         * Efeito "Bounce" ao tocar no card.
         */
        @SuppressLint("ClickableViewAccessibility")
        private fun setupClickAnimation(view: View, recipe: Recipe) {
            view.setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        v.animate().scaleX(0.95f).scaleY(0.95f).setDuration(100).start()
                    }

                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        v.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
                    }
                }
                false
            }

            view.setOnClickListener {
                onRecipeClick(recipe)
            }
        }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<Recipe>() {
            override fun areItemsTheSame(oldItem: Recipe, newItem: Recipe): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: Recipe, newItem: Recipe): Boolean {
                return oldItem == newItem
            }
        }
    }
}