package com.photoncam.ui.filmselect

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.photoncam.film.FilmBrand
import com.photoncam.film.FilmCatalog
import com.photoncam.film.FilmCategory
import com.photoncam.film.FilmStock
import com.photoncam.film.LutType

@Composable
fun FilmSelectorSheet(
    films: List<FilmStock>,
    selected: FilmStock,
    favoriteFilmIds: Set<String>,
    onSelect: (FilmStock) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var activeBrand    by remember { mutableStateOf<FilmBrand?>(null) }
    var activeCategory by remember { mutableStateOf<FilmCategory?>(null) }
    var activeLutType  by remember { mutableStateOf<LutType?>(null) }
    var favoritesOnly  by remember { mutableStateOf(false) }

    val displayed = films
        .let { if (activeBrand    != null) it.filter { f -> f.brand    == activeBrand    } else it }
        .let { if (activeCategory != null) it.filter { f -> f.category == activeCategory } else it }
        .let { if (activeLutType  != null) it.filter { f -> f.lutType  == activeLutType  } else it }
        .let { if (favoritesOnly)          it.filter { f -> f.id in favoriteFilmIds }      else it }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .background(Color(0xFF161616)),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {

            // Handle bar
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 10.dp, bottom = 4.dp)
                    .size(width = 40.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFF555555))
                    .clickable(onClick = onDismiss),
            )

            // Header row: title + close button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 12.dp, top = 4.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "SELECT FILM",
                    color = Color(0xFF888888),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    letterSpacing = 3.sp,
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF2A2A2A))
                        .border(1.dp, Color(0xFF3C3C3C), RoundedCornerShape(4.dp))
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 12.dp, vertical = 5.dp),
                ) {
                    Text(
                        text = "✕ CLOSE",
                        color = Color(0xFF888888),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        letterSpacing = 1.sp,
                    )
                }
            }

            // ── Brand filter row ──────────────────────────────────────────────
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 6.dp),
            ) {
                item {
                    BrandChip(
                        label = "ALL",
                        active = activeBrand == null,
                        color = Color(0xFFCCCCCC),
                        onClick = { activeBrand = null },
                    )
                }
                items(FilmBrand.entries) { brand ->
                    BrandChip(
                        label = brand.displayName.uppercase(),
                        active = activeBrand == brand,
                        color = FilmCatalog.byBrand(brand).firstOrNull()?.accentColor ?: Color.White,
                        onClick = { activeBrand = if (activeBrand == brand) null else brand },
                    )
                }
            }

            // ── Category filter row (incl. FAVORITES) ─────────────────────────
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(bottom = 6.dp),
            ) {
                item {
                    CategoryChip(
                        label = "★ FAV",
                        active = favoritesOnly,
                        color = Color(0xFFFFD700),
                        onClick = { favoritesOnly = !favoritesOnly },
                    )
                }
                item {
                    CategoryChip(
                        label = "ALL",
                        active = activeCategory == null,
                        color = Color(0xFF888888),
                        onClick = { activeCategory = null },
                    )
                }
                items(FilmCategory.entries) { cat ->
                    val catColor = when (cat) {
                        FilmCategory.COLOR          -> Color(0xFFE8A040)
                        FilmCategory.BLACK_AND_WHITE -> Color(0xFFCCCCCC)
                        FilmCategory.SLIDE          -> Color(0xFF5BA4CF)
                        FilmCategory.CROSS_PROCESS  -> Color(0xFFFF6B35)
                        FilmCategory.INSTANT        -> Color(0xFFAA99BB)
                        FilmCategory.INFRARED       -> Color(0xFF8899AA)
                    }
                    CategoryChip(
                        label = cat.displayName.uppercase(),
                        active = activeCategory == cat,
                        color = catColor,
                        onClick = { activeCategory = if (activeCategory == cat) null else cat },
                    )
                }
            }

            // ── LUT type filter row ───────────────────────────────────────────
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(bottom = 10.dp),
            ) {
                item {
                    CategoryChip(
                        label = "ALL LUT",
                        active = activeLutType == null,
                        color = Color(0xFF888888),
                        onClick = { activeLutType = null },
                    )
                }
                items(LutType.entries) { lut ->
                    val lutColor = when (lut) {
                        LutType.PNG    -> Color(0xFF6AB06A)
                        LutType.CUBE   -> Color(0xFF6A88D0)
                        LutType.PRESET -> Color(0xFF888888)
                    }
                    CategoryChip(
                        label = lut.name,
                        active = activeLutType == lut,
                        color = lutColor,
                        onClick = { activeLutType = if (activeLutType == lut) null else lut },
                    )
                }
            }

            HorizontalDivider(color = Color(0xFF2A2A2A))

            // ── Film list ─────────────────────────────────────────────────────
            LazyColumn(
                contentPadding = PaddingValues(vertical = 4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp),
            ) {
                items(displayed) { film ->
                    FilmRow(
                        film = film,
                        isSelected = film.id == selected.id,
                        isFavorite = film.id in favoriteFilmIds,
                        onToggleFavorite = { onToggleFavorite(film.id) },
                        onClick = { onSelect(film) },
                    )
                }
            }

            HorizontalDivider(color = Color(0xFF2A2A2A))
        }
    }
}

@Composable
private fun CategoryChip(label: String, active: Boolean, color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(if (active) color.copy(alpha = 0.18f) else Color.Transparent)
            .border(1.dp, if (active) color else Color(0xFF2E2E2E), RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = label,
            color = if (active) color else Color(0xFF666666),
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            letterSpacing = 1.sp,
        )
    }
}

@Composable
private fun BrandChip(label: String, active: Boolean, color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(if (active) color.copy(alpha = 0.2f) else Color.Transparent)
            .border(1.dp, if (active) color else Color(0xFF3A3A3A), RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = label,
            color = if (active) color else Color(0xFF888888),
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            letterSpacing = 1.sp,
        )
    }
}

@Composable
private fun FilmRow(
    film: FilmStock,
    isSelected: Boolean,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(if (isSelected) film.accentColor.copy(alpha = 0.08f) else Color.Transparent)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = CenterVertically,
    ) {
        // Film canister icon
        FilmCanisterIcon(color = film.accentColor)

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = film.name,
                color = if (isSelected) film.accentColor else Color.White,
                fontFamily = FontFamily.Monospace,
                fontSize = 15.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            )
            Text(
                text = film.description,
                color = Color(0xFF666666),
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
            )
        }

        // ISO badge
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(3.dp))
                .background(Color(0xFF2A2A2A))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        ) {
            Text(
                text = "ISO ${film.iso}",
                color = Color(0xFF888888),
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
            )
        }

        Spacer(modifier = Modifier.width(6.dp))

        // Favorite star
        Text(
            text = if (isFavorite) "★" else "☆",
            color = if (isFavorite) Color(0xFFFFD700) else Color(0xFF383838),
            fontSize = 20.sp,
            modifier = Modifier
                .clickable(onClick = onToggleFavorite)
                .padding(horizontal = 4.dp),
        )

        Spacer(modifier = Modifier.width(4.dp))

        // LUT type badge
        val (lutBg, lutFg, lutLabel) = when (film.lutType) {
            LutType.PNG    -> Triple(Color(0xFF1A2E1A), Color(0xFF6AB06A), "PNG")
            LutType.CUBE   -> Triple(Color(0xFF1A1E30), Color(0xFF6A88D0), "CUBE")
            LutType.PRESET -> Triple(Color(0xFF222222), Color(0xFF666666), "PRESET")
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(3.dp))
                .background(lutBg)
                .padding(horizontal = 6.dp, vertical = 2.dp),
        ) {
            Text(text = lutLabel, color = lutFg, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
        }
    }
}

// ── Film canister icon — Canvas-drawn 35mm canister ───────────────────────────

@Composable
private fun FilmCanisterIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(width = 24.dp, height = 38.dp)) {
        val w = size.width
        val h = size.height
        val capW = w * 0.55f
        val capH = h * 0.16f
        val tongueW = capW * 0.35f
        val tongueH = capH * 0.7f
        val bodyCorner = CornerRadius(3.dp.toPx())

        // Film tongue (tiny protrusion above cap)
        drawRoundRect(
            color = color.copy(alpha = 0.45f),
            topLeft = Offset((w - tongueW) / 2f, 0f),
            size = Size(tongueW, tongueH),
            cornerRadius = CornerRadius(1.5f.dp.toPx()),
        )

        // Cap (slightly wider, darker)
        drawRoundRect(
            color = color.copy(alpha = 0.65f),
            topLeft = Offset((w - capW) / 2f, tongueH * 0.6f),
            size = Size(capW, capH),
            cornerRadius = CornerRadius(2.dp.toPx()),
        )

        // Body (full width, rounded)
        val bodyTop = tongueH * 0.6f + capH * 0.7f
        val bodyH = h - bodyTop
        drawRoundRect(
            color = color,
            topLeft = Offset(0f, bodyTop),
            size = Size(w, bodyH),
            cornerRadius = bodyCorner,
        )

        // Highlight stripe (left side gloss effect)
        drawRoundRect(
            color = Color.White.copy(alpha = 0.18f),
            topLeft = Offset(w * 0.12f, bodyTop + bodyH * 0.12f),
            size = Size(w * 0.14f, bodyH * 0.65f),
            cornerRadius = CornerRadius(2.dp.toPx()),
        )

        // Shadow stripe on right
        drawRoundRect(
            color = Color.Black.copy(alpha = 0.2f),
            topLeft = Offset(w * 0.74f, bodyTop + bodyH * 0.08f),
            size = Size(w * 0.14f, bodyH * 0.7f),
            cornerRadius = CornerRadius(2.dp.toPx()),
        )
    }
}
