package com.luizeduardobrandao.appreceitascha.domain.recipes

/**
 * Modelo de domínio para uma categoria de receitas de chá.
 *
 * Espelha o nó do Realtime Database:
 *   /categories/{categoryId}
 *
 * Campos:
 * - [id]           → identificador estável usado como chave no Firebase (ex.: "calmante").
 * - [name]         → nome amigável exibido na UI (ex.: "Chás Calmantes e Antiestresse").
 * - [description]  → descrição curta, temática, usada em CategoriesFragment.
 * - [order]        → índice numérico para ordenação (1, 2, 3...), conforme regra de negócio.
 *
 * Esta classe é usada na camada de domínio e UI (ViewModels / Fragments),
 * desacoplada de como o Firebase representa internamente os dados.
 */
data class Category(
    val id: String,
    val name: String,
    val description: String,
    val order: Int
)