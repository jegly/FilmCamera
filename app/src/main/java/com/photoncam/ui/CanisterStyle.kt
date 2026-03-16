package com.photoncam.ui

import androidx.compose.ui.graphics.Color
import com.photoncam.film.FilmStock

internal data class CanisterStyle(
    val bodyColor: Color,
    val capColor: Color = Color(0xFF1C1C1C),
    val stripeColor: Color? = null,
)

@Suppress("CyclomaticComplexMethod")
internal fun filmCanisterStyle(film: FilmStock): CanisterStyle = when (film.id) {

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
