package com.example.overlayscreen

import androidx.annotation.DrawableRes

data class BuiltinBackground(
    val key: String,
    @DrawableRes val drawableRes: Int,
    val title: String,
)

object BuiltinBackgrounds {
    const val PREFIX = "builtin:"

    val presets: List<BuiltinBackground> = listOf(
        BuiltinBackground("frosted_glass", R.drawable.frosted_glass, "Frosted Glass"),
        BuiltinBackground("carbon_fiber", R.drawable.carbon_fiber, "Carbon Fiber"),
        BuiltinBackground("matte_ink", R.drawable.matte_ink, "Matte Ink"),
        BuiltinBackground("noise_shield", R.drawable.noise_shield, "Noise Shield"),
        BuiltinBackground("blueprint", R.drawable.blueprint, "Blueprint"),
        BuiltinBackground("concrete", R.drawable.concrete, "Concrete"),
        BuiltinBackground("crt_static", R.drawable.crt_static, "CRT Static"),
        BuiltinBackground("smoke", R.drawable.smoke, "Smoke"),
        BuiltinBackground("velvet", R.drawable.velvet, "Velvet"),
        BuiltinBackground("cipher", R.drawable.cipher, "Cipher"),
        BuiltinBackground("camouflage", R.drawable.camouflage, "Camouflage"),
        BuiltinBackground("halftone", R.drawable.halftone, "Halftone"),
        BuiltinBackground("pixelated_grain_background", R.drawable.pixelated_grain_background, "Pixel Grain"),
        BuiltinBackground("pixelated_background", R.drawable.pixelated_background, "Pixel Mosaic"),
        BuiltinBackground("pixelated_dawn_grid", R.drawable.pixelated_dawn_grid, "Dawn Grid"),
        BuiltinBackground("pixelated_cobalt_blocks", R.drawable.pixelated_cobalt_blocks, "Cobalt Blocks"),
        BuiltinBackground("pixelated_mint_noise", R.drawable.pixelated_mint_noise, "Mint Noise"),
        BuiltinBackground("pixelated_ember_fade", R.drawable.pixelated_ember_fade, "Ember Fade"),
        BuiltinBackground("pixelated_orchid_tiles", R.drawable.pixelated_orchid_tiles, "Orchid Tiles"),
        BuiltinBackground("pixelated_aurora_mesh", R.drawable.pixelated_aurora_mesh, "Aurora Mesh"),
        BuiltinBackground("pixelated_sandstorm", R.drawable.pixelated_sandstorm, "Sandstorm"),
        BuiltinBackground("pixelated_midnight_shards", R.drawable.pixelated_midnight_shards, "Midnight Shards"),
        BuiltinBackground("pixelated_noir_lattice", R.drawable.pixelated_noir_lattice, "Noir Lattice"),
        BuiltinBackground("pixelated_deep_static", R.drawable.pixelated_deep_static, "Deep Static"),
        BuiltinBackground("pixelated_ink_maze", R.drawable.pixelated_ink_maze, "Ink Maze"),
        BuiltinBackground("pixelated_obsidian_grid", R.drawable.pixelated_obsidian_grid, "Obsidian Grid"),
        BuiltinBackground("pixelated_velvet_noise", R.drawable.pixelated_velvet_noise, "Velvet Noise"),
        BuiltinBackground("pixelated_burnished_pixels", R.drawable.pixelated_burnished_pixels, "Burnished Pixels"),
    )

    fun encode(key: String): String = "$PREFIX$key"

    fun decode(value: String?): BuiltinBackground? {
        if (value.isNullOrBlank() || !value.startsWith(PREFIX)) return null
        val key = value.removePrefix(PREFIX)
        return presets.firstOrNull { it.key == key }
    }
}
