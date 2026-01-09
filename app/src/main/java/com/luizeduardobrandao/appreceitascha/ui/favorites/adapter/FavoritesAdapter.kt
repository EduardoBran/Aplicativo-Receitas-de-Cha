package com.luizeduardobrandao.appreceitascha.ui.favorites.adapter

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.luizeduardobrandao.appreceitascha.R
import com.luizeduardobrandao.appreceitascha.databinding.ItemFavoriteBinding
import com.luizeduardobrandao.appreceitascha.domain.recipes.Recipe
import com.luizeduardobrandao.appreceitascha.ui.favorites.FavoriteListItem

class FavoritesAdapter(
    private val canOpenRecipe: (Recipe) -> Boolean,
    private val onOpenRecipe: (Recipe) -> Unit
) : ListAdapter<FavoriteListItem, RecyclerView.ViewHolder>(FavoritesDiffCallback) {

    private var lastAnimatedPosition = -1

    companion object {
        const val TYPE_HEADER = 0
        const val TYPE_ITEM = 1
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is FavoriteListItem.Header -> TYPE_HEADER
            is FavoriteListItem.RecipeItem -> TYPE_ITEM
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_HEADER) {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_search_header, parent, false)
            HeaderViewHolder(view)
        } else {
            val binding = ItemFavoriteBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            RecipeViewHolder(binding, canOpenRecipe, onOpenRecipe)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is FavoriteListItem.Header -> (holder as HeaderViewHolder).bind(item)
            is FavoriteListItem.RecipeItem -> (holder as RecipeViewHolder).bind(item.recipe)
        }
        setEnterAnimation(holder.itemView, position)
    }

    private fun setEnterAnimation(viewToAnimate: View, position: Int) {
        if (position > lastAnimatedPosition) {
            val animation =
                AnimationUtils.loadAnimation(viewToAnimate.context, android.R.anim.slide_in_left)
            animation.duration = 350
            viewToAnimate.startAnimation(animation)
            lastAnimatedPosition = position
        }
    }

    override fun onViewDetachedFromWindow(holder: RecyclerView.ViewHolder) {
        holder.itemView.clearAnimation()
    }

    // --- ViewHolders ---

    class HeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvTitle: TextView = itemView.findViewById(R.id.tvHeaderTitle)
        private val divider: View = itemView.findViewById(R.id.viewDivider)

        fun bind(item: FavoriteListItem.Header) {
            tvTitle.text = item.categoryName
            divider.isVisible = item.showDivider
        }
    }

    class RecipeViewHolder(
        private val binding: ItemFavoriteBinding,
        private val canOpenRecipe: (Recipe) -> Boolean,
        private val onOpenRecipe: (Recipe) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(recipe: Recipe) {
            binding.textFavoriteTitle.text = recipe.title
            binding.textFavoriteSubtitle.text = recipe.subtitle

            val isUnlocked = canOpenRecipe(recipe)
            val iconRes = if (isUnlocked) {
                R.drawable.ic_recipe_lock_open_24
            } else {
                R.drawable.ic_recipe_lock_24
            }
            binding.imageFavoriteLock.setImageResource(iconRes)

            setupClickAnimation(binding.cardFavoriteRoot, recipe)
        }

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
                onOpenRecipe(recipe)
            }
        }
    }

    private object FavoritesDiffCallback : DiffUtil.ItemCallback<FavoriteListItem>() {
        @Suppress("IntroduceWhenSubject")
        override fun areItemsTheSame(
            oldItem: FavoriteListItem,
            newItem: FavoriteListItem
        ): Boolean {
            return when {
                oldItem is FavoriteListItem.RecipeItem && newItem is FavoriteListItem.RecipeItem ->
                    oldItem.recipe.id == newItem.recipe.id

                oldItem is FavoriteListItem.Header && newItem is FavoriteListItem.Header ->
                    oldItem.categoryName == newItem.categoryName

                else -> false
            }
        }

        override fun areContentsTheSame(
            oldItem: FavoriteListItem,
            newItem: FavoriteListItem
        ): Boolean {
            return oldItem == newItem
        }
    }
}