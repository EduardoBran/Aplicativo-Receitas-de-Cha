package com.luizeduardobrandao.appreceitascha.domain.recipes

/**
 * Modelo de domínio para uma receita de chá.
 *
 * Espelha o nó do Realtime Database:
 *   /recipes/{recipeId}
 *
 * Campos:
 * - [id]               → identificador único da receita (ex.: "r1", "r2"...).
 * - [categoryId]       → id da categoria à qual a receita pertence (ex.: "calmante").
 * - [title]            → título principal exibido na lista e nos detalhes.
 * - [subtitle]         → subtítulo (frase de impacto), exibido na lista/detalhes.
 * - [shortDescription] → resumo curto para exibição em listas (RecipeListFragment, Favorites).
 * - [modoDePreparo]    → texto completo com o passo a passo da preparação.
 * - [beneficios]       → texto explicando potenciais benefícios do chá.
 * - [observacoes]      → avisos, cuidados e observações importantes.
 * - [isFreePreview]    → flag central da regra de negócios:
 *      true  → receita gratuita (preview), acessível no modo gratuito.
 *      false → receita premium, acessível apenas com plano ativo.
 *
 * Regras de negócio associadas:
 * - Para cada categoria, deve existir exatamente UMA receita com [isFreePreview] = true.
 * - Usuário NAO_LOGADO ou LOGADO + SEM_PLANO:
 *      → só pode abrir a receita com [isFreePreview] = true em cada categoria.
 * - Usuário LOGADO + COM_PLANO:
 *      → pode acessar todas as receitas (independente de [isFreePreview]).
 *
 * Esta classe é usada nas camadas de domínio e UI (RecipeListViewModel,
 * RecipeDetailViewModel, FavoritesViewModel, etc.), enquanto a camada data
 * se encarrega de mapear o formato bruto do Firebase para este modelo.
 */
data class Recipe(
    val id: String,
    val categoryId: String,
    val title: String,
    val subtitle: String,
    val shortDescription: String,
    val modoDePreparo: String,
    val beneficios: String,
    val observacoes: String,
    val isFreePreview: Boolean
)