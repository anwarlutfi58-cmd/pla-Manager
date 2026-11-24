package com.pla.manajer.util

object VideoStickerHelper {
    val stickers = arrayOf("Hati (Love)", "Bintang (Star)", "Emoji Senyum", "Panah (Arrow)", "Api (Fire)", "Kilau (Sparkle)")

    fun getStickerResId(name: String): Int {
        return when (name) {
            "Hati (Love)" -> android.R.drawable.ic_menu_myplaces
            "Bintang (Star)" -> android.R.drawable.btn_star_big_on
            "Emoji Senyum" -> android.R.drawable.ic_menu_info_details
            "Panah (Arrow)" -> android.R.drawable.ic_media_play
            "Api (Fire)" -> android.R.drawable.ic_menu_camera
            "Kilau (Sparkle)" -> android.R.drawable.ic_menu_gallery
            else -> android.R.drawable.ic_menu_help
        }
    }
}