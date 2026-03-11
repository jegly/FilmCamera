package com.photoncam.ui.filmselect

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import com.photoncam.film.FilmStock

@Composable
fun FilmSelectorSheet(
    films: List<FilmStock>,
    selected: FilmStock,
    onSelect: (FilmStock) -> Unit,
    onDismiss: () -> Unit,
) {
    var activeBrand by remember { mutableStateOf<FilmBrand?>(null) }
    val displayed = if (activeBrand != null) films.filter { it.brand == activeBrand } else films

    Box(
        modifier = Modifier
            .fillMaxWidth()
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

            Text(
                text = "SELECT FILM",
                color = Color(0xFF888888),
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                letterSpacing = 3.sp,
                modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
            )

            // Brand filter row
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 12.dp),
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

            HorizontalDivider(color = Color(0xFF2A2A2A))

            // Film list
            LazyColumn(
                contentPadding = PaddingValues(vertical = 4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(340.dp),
            ) {
                items(displayed) { film ->
                    FilmRow(
                        film = film,
                        isSelected = film.id == selected.id,
                        onClick = { onSelect(film) },
                    )
                }
            }
        }
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
private fun FilmRow(film: FilmStock, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(if (isSelected) film.accentColor.copy(alpha = 0.08f) else Color.Transparent)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
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
