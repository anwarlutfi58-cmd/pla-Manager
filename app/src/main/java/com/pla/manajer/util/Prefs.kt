package com.pla.manajer.util

import android.content.Context
import android.content.SharedPreferences

class Prefs(context: Context) {
    private val ctx: Context = context.applicationContext
    private val prefs: SharedPreferences =
        ctx.getSharedPreferences("pla_prefs", Context.MODE_PRIVATE)

    var lastPath: String
        get() = prefs.getString("last_path", ctx.getExternalFilesDir(null)?.absolutePath ?: "/") ?: "/"
        set(value) = prefs.edit().putString("last_path", value).apply()

    var trashFolder: String
        get() = prefs.getString("trash_folder", "${ctx.filesDir.absolutePath}/.trash")
            ?: "${ctx.filesDir.absolutePath}/.trash"
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

    var editorFontSize: Int
        get() = prefs.getInt("editor_font_size", 14)
        set(value) = prefs.edit().putInt("editor_font_size", value).apply()

    var screenReaderHints: Boolean
        get() = prefs.getBoolean("screen_reader_hints", true)
        set(value) = prefs.edit().putBoolean("screen_reader_hints", value).apply()

    var showDateTime: Boolean
        get() = prefs.getBoolean("show_date_time", false)
        set(value) = prefs.edit().putBoolean("show_date_time", value).apply()
}