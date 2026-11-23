package com.pla.manajer.util

import android.content.Context
import android.content.SharedPreferences

class Prefs(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("pla_prefs", Context.MODE_PRIVATE)

    var lastPath: String
        get() = prefs.getString("last_path", context.getExternalFilesDir(null)?.absolutePath ?: "/") ?: "/"
        set(value) = prefs.edit().putString("last_path", value).apply()

    var trashFolder: String
        get() = prefs.getString("trash_folder", "${context.filesDir.absolutePath}/.trash") ?: "${context.filesDir.absolutePath}/.trash"
        set(value) = prefs.edit().putString("trash_folder", value).apply()

    var sortMode: Int
        get() = prefs.getInt("sort_mode", 0)
        set(value) = prefs.edit().putInt("sort_mode", value).apply()

    var showImageEditor: Boolean
        get() = prefs.getBoolean("show_image_editor", true)
        set(value) = prefs.edit().putBoolean("show_image_editor", value).apply()

    var showAudioEditor: Boolean
        get() = prefs.getBoolean("show_audio_editor", true)
        set(value) = prefs.edit().putBoolean("show_audio_editor", value).apply()

    var showVideoEditor: Boolean
        get() = prefs.getBoolean("show_video_editor", true)
        set(value) = prefs.edit().putBoolean("show_video_editor", value).apply()

    var showGithubBuilder: Boolean
        get() = prefs.getBoolean("show_github_builder", true)
        set(value) = prefs.edit().putBoolean("show_github_builder", value).apply()

    // TAMBAHAN BARU: Pengaturan Tampilkan Jam
    var showDateTime: Boolean
        get() = prefs.getBoolean("show_date_time", false)
        set(value) = prefs.edit().putBoolean("show_date_time", value).apply()
}