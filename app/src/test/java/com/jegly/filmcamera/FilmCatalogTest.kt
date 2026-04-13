package com.jegly.filmcamera

import com.jegly.filmcamera.film.FilmBrand
import com.jegly.filmcamera.film.FilmCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FilmCatalogTest {

    @Test
    fun `catalog contains at least one stock per brand`() {
        FilmBrand.entries.forEach { brand ->
            assertTrue(
                "No film stocks for brand $brand",
                FilmCatalog.byBrand(brand).isNotEmpty(),
            )
        }
    }

    @Test
    fun `findById returns correct stock`() {
        val film = FilmCatalog.findById("kodak_portra_400")
        assertNotNull(film)
        assertEquals("Portra 400", film!!.name)
    }

    @Test
    fun `default stock is Portra 400`() {
        assertEquals("kodak_portra_400", FilmCatalog.default.id)
    }

    @Test
    fun `grain amount is within valid range`() {
        FilmCatalog.all.forEach { film ->
            assertTrue("${film.id} grain out of range", film.grainAmount in 0f..1f)
            assertTrue("${film.id} light leak prob out of range", film.lightLeakProbability in 0f..1f)
        }
    }
}
