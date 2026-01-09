package com.luizeduardobrandao.appreceitascha.ui.search.adapter

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
import com.luizeduardobrandao.appreceitascha.databinding.ItemRecipeBinding
import com.luizeduardobrandao.appreceitascha.domain.recipes.Recipe
import com.luizeduardobrandao.appreceitascha.ui.search.SearchListItem

class SearchAdapter(
    private val canOpenRecipe: (Recipe) -> Boolean,
    private val onRecipeClick: (Recipe) -> Unit
) : ListAdapter<SearchListItem, RecyclerView.ViewHolder>(SearchDiffCallback()) {

    private var lastAnimatedPosition = -1

    companion object {
        const val TYPE_HEADER = 0
        const val TYPE_ITEM = 1
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is SearchListItem.Header -> TYPE_HEADER
            is SearchListItem.RecipeItem -> TYPE_ITEM
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_HEADER) {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_search_header, parent, false)
            HeaderViewHolder(view)
        } else {
            val binding = ItemRecipeBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            RecipeViewHolder(binding, canOpenRecipe, onRecipeClick)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is SearchListItem.Header -> (holder as HeaderViewHolder).bind(item)
            is SearchListItem.RecipeItem -> (holder as RecipeViewHolder).bind(item.recipe)
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

        fun bind(item: SearchListItem.Header) {
            tvTitle.text = item.categoryName
            divider.isVisible = item.showDivider
        }
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
            val iconRes =
                if (isUnlocked) R.drawable.ic_recipe_lock_open_24 else R.drawable.ic_recipe_lock_24

            val context = binding.root.context
            val desc = if (isUnlocked) {
                context.getString(R.string.search_recipe_unlocked)
            } else {
                context.getString(R.string.search_recipe_locked)
            }

            binding.imageRecipeLock.setImageResource(iconRes)
            binding.imageRecipeLock.contentDescription = desc

            setupClickAnimation(binding.cardRecipe, recipe)
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
            view.setOnClickListener { onRecipeClick(recipe) }
        }
    }
}

class SearchDiffCallback : DiffUtil.ItemCallback<SearchListItem>() {
    @Suppress("IntroduceWhenSubject")
    override fun areItemsTheSame(oldItem: SearchListItem, newItem: SearchListItem): Boolean {
        return when {
            oldItem is SearchListItem.RecipeItem && newItem is SearchListItem.RecipeItem ->
                oldItem.recipe.id == newItem.recipe.id

            oldItem is SearchListItem.Header && newItem is SearchListItem.Header ->
                oldItem.categoryName == newItem.categoryName

            else -> false
        }
    }

    override fun areContentsTheSame(oldItem: SearchListItem, newItem: SearchListItem): Boolean {
        return oldItem == newItem
    }
}