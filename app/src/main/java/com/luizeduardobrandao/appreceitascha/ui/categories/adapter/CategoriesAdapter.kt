package com.luizeduardobrandao.appreceitascha.ui.categories.adapter

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
import com.luizeduardobrandao.appreceitascha.databinding.ItemCategoryBinding
import com.luizeduardobrandao.appreceitascha.domain.recipes.Category

class CategoriesAdapter(
    private val onCategoryClick: (Category) -> Unit
) : ListAdapter<Category, CategoriesAdapter.CategoryViewHolder>(DIFF_CALLBACK) {

    private var lastAnimatedPosition = -1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemCategoryBinding.inflate(inflater, parent, false)
        return CategoryViewHolder(binding, onCategoryClick)
    }

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        holder.bind(getItem(position))
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

    override fun onViewDetachedFromWindow(holder: CategoryViewHolder) {
        holder.itemView.clearAnimation()
    }

    class CategoryViewHolder(
        private val binding: ItemCategoryBinding,
        private val onCategoryClick: (Category) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(category: Category) {
            binding.textCategoryName.text = category.name
            binding.textCategoryDescription.text = category.description

            val iconRes = getIconForCategoryId(category.id)
            binding.imageCategoryIcon.setImageResource(iconRes)

            // Configura a animação de clique e o listener
            setupClickAnimation(binding.cardCategory, category)
        }

        /**
         * Adiciona um efeito de "Bounce" (escala) ao toque.
         * Usa OnTouchListener para garantir a animação visual antes do clique.
         */
        @SuppressLint("ClickableViewAccessibility")
        private fun setupClickAnimation(view: View, category: Category) {
            view.setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        // Diminui levemente ao tocar (0.95 é mais visível que 0.98)
                        v.animate().scaleX(0.95f).scaleY(0.95f).setDuration(100).start()
                    }

                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        // Volta ao tamanho original
                        v.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
                    }
                }
                // Retorna false para permitir que o OnClickListener funcione normalmente
                false
            }

            view.setOnClickListener {
                onCategoryClick(category)
            }
        }

        private fun getIconForCategoryId(categoryId: String): Int {
            return when (categoryId) {
                "digestivo" -> R.drawable.ic_spa_24
                "imunidade" -> R.drawable.ic_shield_with_heart_24
                "beleza" -> R.drawable.ic_face_24
                "medicinal" -> R.drawable.ic_medical_services_24
                "afrodisiaco" -> R.drawable.ic_fire_24
                "emagrecimento" -> R.drawable.ic_monitor_weight_24
                "calmante" -> R.drawable.ic_bedtime_stars_24
                "energizante" -> R.drawable.ic_rocket_launch_24
                else -> R.drawable.ic_tea_48
            }
        }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<Category>() {
            override fun areItemsTheSame(oldItem: Category, newItem: Category) =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: Category, newItem: Category) =
                oldItem == newItem
        }
    }
}