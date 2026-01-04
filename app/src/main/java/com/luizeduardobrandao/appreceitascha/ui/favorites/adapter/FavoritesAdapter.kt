package com.luizeduardobrandao.appreceitascha.ui.favorites.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.luizeduardobrandao.appreceitascha.R
import com.luizeduardobrandao.appreceitascha.databinding.ItemFavoriteBinding
import com.luizeduardobrandao.appreceitascha.domain.recipes.Recipe

class FavoritesAdapter(
    private val canOpenRecipe: (Recipe) -> Boolean, // Novo parâmetro
    private val onOpenRecipe: (Recipe) -> Unit
) : ListAdapter<Recipe, FavoritesAdapter.FavoritesViewHolder>(FavoritesDiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FavoritesViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemFavoriteBinding.inflate(inflater, parent, false)
        return FavoritesViewHolder(binding, canOpenRecipe, onOpenRecipe)
    }

    override fun onBindViewHolder(holder: FavoritesViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class FavoritesViewHolder(
        private val binding: ItemFavoriteBinding,
        private val canOpenRecipe: (Recipe) -> Boolean,
        private val onOpenRecipe: (Recipe) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(recipe: Recipe) {
            binding.textFavoriteTitle.text = recipe.title
            binding.textFavoriteSubtitle.text = recipe.subtitle

            // Lógica do Cadeado (Igual ao RecipesAdapter)
            val isUnlocked = canOpenRecipe(recipe)

            val iconRes = if (isUnlocked) {
                R.drawable.ic_recipe_lock_open_24
            } else {
                R.drawable.ic_recipe_lock_24
            }
            binding.imageFavoriteLock.setImageResource(iconRes)

            // Clique no Card
            binding.cardFavoriteRoot.setOnClickListener {
                onOpenRecipe(recipe)
            }
        }
    }

    private object FavoritesDiffCallback : DiffUtil.ItemCallback<Recipe>() {
        override fun areItemsTheSame(oldItem: Recipe, newItem: Recipe): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Recipe, newItem: Recipe): Boolean {
            return oldItem == newItem
        }
    }
}