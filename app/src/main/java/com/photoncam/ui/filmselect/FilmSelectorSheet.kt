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
import androidx.compose.ui.graphics.Brush
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
                        label = lut.displayName,
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
        // Film canister icon — brand/film-specific appearance
        FilmCanisterIcon(film = film)

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

        // Favorite star — large, left of ISO
        Text(
            text = if (isFavorite) "★" else "☆",
            color = if (isFavorite) Color(0xFFFFD700) else Color(0xFF3A3A3A),
            fontSize = 28.sp,
            modifier = Modifier
                .clickable(onClick = onToggleFavorite)
                .padding(horizontal = 6.dp),
        )

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

        // LUT type badge
        val (lutBg, lutFg, lutLabel) = when (film.lutType) {
            LutType.PNG    -> Triple(Color(0xFF1A2E1A), Color(0xFF6AB06A), LutType.PNG.displayName)
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

// ── Canister style per film ────────────────────────────────────────────────────

private data class CanisterStyle(
    val bodyColor: Color,
    val capColor: Color = Color(0xFF1C1C1C),
    val stripeColor: Color? = null,
)

@Suppress("CyclomaticComplexMethod")
private fun filmCanisterStyle(film: FilmStock): CanisterStyle = when (film.id) {

    // ── Kodak ─────────────────────────────────────────────────────────────────
    "kodak_kodachrome_25", "kodak_kodachrome_64", "kodak_kodachrome_200" ->
        CanisterStyle(Color(0xFFCC2211), Color(0xFF1A1A1A), Color(0xFFFFD700))
    "kodak_portra_160", "kodak_portra_400", "kodak_portra_400_nc",
    "kodak_portra_400_uc", "kodak_portra_400_vc", "kodak_portra_800" ->
        CanisterStyle(Color(0xFFD4AA7A), Color(0xFF1E1A14), Color(0xFF8B6030))
    "kodak_ektar_100" ->
        CanisterStyle(Color(0xFFCC1100), Color(0xFF1A1A1A), Color(0xFFFFCC00))
    "kodak_gold_200" ->
        CanisterStyle(Color(0xFFF5C800), Color(0xFF1A1A1A), Color(0xFFAA8800))
    "kodak_ektachrome_100vs", "kodak_ektachrome_e100" ->
        CanisterStyle(Color(0xFF1C4A8A), Color(0xFF0A1828), Color(0xFF5B9BD5))
    "kodak_elite100_xpro", "kodak_elite_color200" ->
        CanisterStyle(Color(0xFF553399), Color(0xFF1A1A1A), Color(0xFFAA66DD))
    "kodak_tmax_100", "kodak_tmax_400", "kodak_tmax_3200" ->
        CanisterStyle(Color(0xFF222222), Color(0xFF0A0A0A), Color(0xFFAAAAAA))
    "kodak_trix_400" ->
        CanisterStyle(Color(0xFFE8B800), Color(0xFF1A1A1A), Color(0xFF1A1A1A))
    "kodak_ultramax_400" ->
        CanisterStyle(Color(0xFF226622), Color(0xFF1A1A1A), Color(0xFFFFCC00))
    "kodak_colorplus_200" ->
        CanisterStyle(Color(0xFF448822), Color(0xFF1A1A1A), Color(0xFFEEEE00))

    // ── Fujifilm ──────────────────────────────────────────────────────────────
    "fuji_velvia_50" ->
        CanisterStyle(Color(0xFFCC2200), Color(0xFF1A1A1A), Color(0xFFFF8800))
    "fuji_provia_100f" ->
        CanisterStyle(Color(0xFF00826A), Color(0xFF0A2220), Color(0xFF00DDAA))
    "fuji_superia_100", "fuji_superia_200", "fuji_superia_400",
    "fuji_superia_800", "fuji_superia_1600" ->
        CanisterStyle(Color(0xFF00A550), Color(0xFF0A2418), Color(0xFFEEEEEE))
    "fuji_superia_200_xpro" ->
        CanisterStyle(Color(0xFF009944), Color(0xFF1A1A1A), Color(0xFFFF5500))
    "fuji_acros_100" ->
        CanisterStyle(Color(0xFF1A1A1A), Color(0xFF080808), Color(0xFFCCCCCC))
    "fuji_neopan_400", "fuji_neopan_1600" ->
        CanisterStyle(Color(0xFF222222), Color(0xFF0A0A0A), Color(0xFFDDDDDD))
    "fuji_160c" ->
        CanisterStyle(Color(0xFF228890), Color(0xFF0A2224), Color(0xFFAADDEE))
    "fuji_pro400h" ->
        CanisterStyle(Color(0xFF006644), Color(0xFF0A2018), Color(0xFF88DDBB))
    "fuji_800z" ->
        CanisterStyle(Color(0xFF005533), Color(0xFF0A1810), Color(0xFF66CC88))
    "fuji_natura_1600" ->
        CanisterStyle(Color(0xFFEEEEDD), Color(0xFF2A2A2A), Color(0xFF009944))

    // ── Ilford ────────────────────────────────────────────────────────────────
    "ilford_panf_50", "ilford_fp4", "ilford_hp5", "ilford_xp2", "ilford_hps_800" ->
        CanisterStyle(Color(0xFFE8B800), Color(0xFF1A1A1A), Color(0xFF1A1A1A))
    "ilford_delta_100", "ilford_delta_400", "ilford_delta_3200" ->
        CanisterStyle(Color(0xFF111111), Color(0xFF050505), Color(0xFF888888))
    "ilford_sfx_200" ->
        CanisterStyle(Color(0xFF881111), Color(0xFF1A1A1A), Color(0xFFDDDDDD))

    // ── Lomography ────────────────────────────────────────────────────────────
    "lomo_xpro", "lomo_xpro_slide_200" ->
        CanisterStyle(Color(0xFFDD3311), Color(0xFF1A1A1A), Color(0xFF11AADD))
    "lomo_redscale_xr" ->
        CanisterStyle(Color(0xFFCC2200), Color(0xFF1A1A1A), Color(0xFFFF8800))
    "lomo_chrome_metropolis" ->
        CanisterStyle(Color(0xFF3A4A5A), Color(0xFF1A1A1A), Color(0xFF88AACC))
    "lomo_chrome_purple" ->
        CanisterStyle(Color(0xFF6633AA), Color(0xFF1A1A1A), Color(0xFFCC88FF))

    // ── CineStill ─────────────────────────────────────────────────────────────
    "cinestill_50d" ->
        CanisterStyle(Color(0xFF1155BB), Color(0xFF0A1833), Color(0xFF88AADD))
    "cinestill_800t" ->
        CanisterStyle(Color(0xFFE84000), Color(0xFF1A1A1A), Color(0xFF111111))

    // ── Agfa ──────────────────────────────────────────────────────────────────
    "agfa_apx_100" ->
        CanisterStyle(Color(0xFF444444), Color(0xFF0A0A0A), Color(0xFFCCCCCC))
    "agfa_precisa_100" ->
        CanisterStyle(Color(0xFF445566), Color(0xFF1A1A2A), Color(0xFF99BBDD))
    "agfa_ultra_color_100" ->
        CanisterStyle(Color(0xFFDD4400), Color(0xFF1A1A1A), Color(0xFFFFAA00))
    "agfa_vista_200" ->
        CanisterStyle(Color(0xFFEE5500), Color(0xFF1A1A1A), Color(0xFFFFCC00))

    // ── Rollei ────────────────────────────────────────────────────────────────
    "rollei_retro_80s", "rollei_retro_100", "rollei_ortho_25" ->
        CanisterStyle(Color(0xFFAA1111), Color(0xFF1A1A1A), Color(0xFFDDDDDD))
    "rollei_ir_400" ->
        CanisterStyle(Color(0xFF880000), Color(0xFF0A0A0A), Color(0xFF441111))

    // ── Polaroid ──────────────────────────────────────────────────────────────
    "polaroid_669", "polaroid_690", "polaroid_px100uv_cold", "polaroid_timezero_expired",
    "polaroid_672", "polaroid_px680", "polaroid_px70", "polaroid_667", "polaroid_665" ->
        CanisterStyle(Color(0xFFEEEEEE), Color(0xFF222222), Color(0xFF333333))

    // Fallback — use film's own accent color
    else -> CanisterStyle(
        bodyColor = film.accentColor,
        capColor = Color(0xFF1C1C1C),
        stripeColor = Color.White.copy(alpha = 0.35f),
    )
}

// ── Film canister icon — brand/film authentic appearance ──────────────────────

@Composable
private fun FilmCanisterIcon(film: FilmStock, modifier: Modifier = Modifier) {
    val style = filmCanisterStyle(film)
    Canvas(modifier = modifier.size(width = 30.dp, height = 46.dp)) {
        val w = size.width
        val h = size.height

        // ── Film tongue (leader exiting from top center) ───────────────────────
        val tongueW = w * 0.30f
        val tongueH = h * 0.11f
        drawRoundRect(
            color = style.bodyColor.copy(alpha = 0.75f),
            topLeft = Offset((w - tongueW) / 2f, 0f),
            size = Size(tongueW, tongueH + 2.dp.toPx()),
            cornerRadius = CornerRadius(1.dp.toPx()),
        )

        // ── Top cap ───────────────────────────────────────────────────────────
        val topCapTop = tongueH * 0.35f
        val topCapH = h * 0.155f
        drawRoundRect(
            color = style.capColor,
            topLeft = Offset(0f, topCapTop),
            size = Size(w, topCapH + 2.dp.toPx()),
            cornerRadius = CornerRadius(3.dp.toPx()),
        )
        // Top cap specular highlight
        drawRect(
            color = Color.White.copy(alpha = 0.13f),
            topLeft = Offset(w * 0.08f, topCapTop + topCapH * 0.15f),
            size = Size(w * 0.84f, topCapH * 0.38f),
        )

        // ── Body ──────────────────────────────────────────────────────────────
        val bodyStartY = topCapTop + topCapH * 0.88f
        val bottomCapH = h * 0.135f
        val bodyEndY = h - bottomCapH * 0.88f
        val bodyH = bodyEndY - bodyStartY

        // Body base color
        drawRect(
            color = style.bodyColor,
            topLeft = Offset(0f, bodyStartY),
            size = Size(w, bodyH),
        )

        // Left cylindrical highlight
        drawRect(
            brush = Brush.horizontalGradient(
                colors = listOf(Color.White.copy(alpha = 0.32f), Color.Transparent),
                startX = 0f,
                endX = w * 0.38f,
            ),
            topLeft = Offset(0f, bodyStartY),
            size = Size(w * 0.38f, bodyH),
        )

        // Right cylindrical shadow
        drawRect(
            brush = Brush.horizontalGradient(
                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.38f)),
                startX = w * 0.55f,
                endX = w,
            ),
            topLeft = Offset(w * 0.55f, bodyStartY),
            size = Size(w * 0.45f, bodyH),
        )

        // Horizontal accent stripe (centered in body — like a label band)
        style.stripeColor?.let { stripe ->
            val stripeY = bodyStartY + bodyH * 0.44f
            val stripeThickness = 2.8.dp.toPx()
            drawRect(
                color = stripe.copy(alpha = 0.82f),
                topLeft = Offset(0f, stripeY),
                size = Size(w, stripeThickness),
            )
        }

        // ── Bottom cap ────────────────────────────────────────────────────────
        val botCapTop = h - bottomCapH - 0.5.dp.toPx()
        drawRoundRect(
            color = style.capColor,
            topLeft = Offset(0f, botCapTop - 2.dp.toPx()),
            size = Size(w, bottomCapH + 2.dp.toPx()),
            cornerRadius = CornerRadius(3.dp.toPx()),
        )
        // Bottom cap specular highlight
        drawRect(
            color = Color.White.copy(alpha = 0.10f),
            topLeft = Offset(w * 0.08f, botCapTop + bottomCapH * 0.20f),
            size = Size(w * 0.84f, bottomCapH * 0.30f),
        )

        // ── Cap-body edge lines (seam detail) ─────────────────────────────────
        drawRect(
            color = Color.Black.copy(alpha = 0.35f),
            topLeft = Offset(0f, bodyStartY - 0.5.dp.toPx()),
            size = Size(w, 1.dp.toPx()),
        )
        drawRect(
            color = Color.Black.copy(alpha = 0.35f),
            topLeft = Offset(0f, bodyEndY - 0.5.dp.toPx()),
            size = Size(w, 1.dp.toPx()),
        )
    }
}
