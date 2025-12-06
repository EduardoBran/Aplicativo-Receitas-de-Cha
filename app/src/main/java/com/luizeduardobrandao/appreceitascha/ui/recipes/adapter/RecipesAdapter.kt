package com.luizeduardobrandao.appreceitascha.ui.recipes.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.luizeduardobrandao.appreceitascha.R
import com.luizeduardobrandao.appreceitascha.databinding.ItemRecipeBinding
import com.luizeduardobrandao.appreceitascha.domain.recipes.Recipe

/**
 * Adapter da lista de receitas de uma categoria.
 *
 * - Usa ListAdapter + DiffUtil para atualizações eficientes.
 * - Mostra ícone de cadeado aberto/fechado conforme pode ou não abrir a receita.
 */
class RecipesAdapter(
    private val canOpenRecipe: (Recipe) -> Boolean,
    private val onRecipeClick: (Recipe) -> Unit
) : ListAdapter<Recipe, RecipesAdapter.RecipeViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecipeViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemRecipeBinding.inflate(inflater, parent, false)
        return RecipeViewHolder(binding, canOpenRecipe, onRecipeClick)
    }

    override fun onBindViewHolder(holder: RecipeViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class RecipeViewHolder(
        private val binding: ItemRecipeBinding,
        private val canOpenRecipe: (Recipe) -> Boolean,
        private val onRecipeClick: (Recipe) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(recipe: Recipe) {
            binding.textRecipeTitle.text = recipe.title
            binding.textRecipeSubtitle.text = recipe.subtitle

            // Decide se a receita está liberada ou bloqueada
            val isUnlocked = canOpenRecipe(recipe)

            val iconRes = if (isUnlocked) {
                R.drawable.ic_recipe_lock_open_24
            } else {
                R.drawable.ic_recipe_lock_24
            }

            val contentDescRes = if (isUnlocked) {
                R.string.recipe_lock_open_content_description
            } else {
                R.string.recipe_lock_closed_content_description
            }

            binding.imageRecipeLock.setImageResource(iconRes)
            binding.imageRecipeLock.contentDescription =
                binding.root.context.getString(contentDescRes)

            // Clique no card: fragment decide se navega ou mostra Snackbar
            binding.cardRecipe.setOnClickListener {
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