package com.jegly.filmcamera.film

object FilmCatalog {

    val all: List<FilmStock> by lazy {
        (stocksKodak + stocksFuji + stocksIlford + stocksRollei + stocksAgfa + stocksPolaroid + stocksLomography)
            .sortedWith(compareBy({ it.iso }, { it.brand.displayName }, { it.name }))
    }

    val default: FilmStock get() =
        all.firstOrNull { it.id == "negative_new_kodak_portra_400_4" } ?: all.first()

    fun findById(id: String): FilmStock? = all.firstOrNull { it.id == id }

    fun byBrand(brand: FilmBrand): List<FilmStock> = all.filter { it.brand == brand }

    fun byCategory(category: FilmCategory): List<FilmStock> = all.filter { it.category == category }

    fun search(query: String): List<FilmStock> {
        val q = query.lowercase().trim()
        if (q.isEmpty()) return all
        return all.filter {
            it.name.lowercase().contains(q) ||
            it.brand.displayName.lowercase().contains(q) ||
            it.category.displayName.lowercase().contains(q) ||
            it.description.lowercase().contains(q)
        }
    }
}
