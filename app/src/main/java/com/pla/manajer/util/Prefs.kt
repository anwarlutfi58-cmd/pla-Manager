package com.pla.manajer.util

import android.content.Context
import android.os.Environment

class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("pla_prefs", Context.MODE_PRIVATE)
    private val appCtx = ctx.applicationContext

    var lastPath: String
        get() = sp.getString("lastPath", Environment.getExternalStorageDirectory().absolutePath) ?: ""
        set(v) = sp.edit().putString("lastPath", v).apply()

    var editorFontSize: Int
        get() = sp.getInt("fontSize", 14)
        set(v) = sp.edit().putInt("fontSize", v).apply()

    var trashFolder: String
        get() = sp.getString("trashFolder", appCtx.filesDir.absolutePath + "/trash") ?: ""
        set(v) = sp.edit().putString("trashFolder", v).apply()

    var screenReaderHints: Boolean
        get() = sp.getBoolean("srHints", true)
        set(v) = sp.edit().putBoolean("srHints", v).apply()

    var sortMode: Int
        get() = sp.getInt("sortMode", 1)
        set(v) = sp.edit().putInt("sortMode", v).apply()

    // ==========================================
    // TAMBAHAN BARU: FITUR TOMBOL AJAIB
    // ==========================================
    var showImageEditor: Boolean
        get() = sp.getBoolean("showImageEditor", false)
        set(v) = sp.edit().putBoolean("showImageEditor", v).apply()

    var showAudioEditor: Boolean
        get() = sp.getBoolean("showAudioEditor", false)
        set(v) = sp.edit().putBoolean("showAudioEditor", v).apply()

    var showVideoEditor: Boolean
        get() = sp.getBoolean("showVideoEditor", false)
        set(v) = sp.edit().putBoolean("showVideoEditor", v).apply()

    var showGithubBuilder: Boolean
        get() = sp.getBoolean("showGithubBuilder", false)
        set(v) = sp.edit().putBoolean("showGithubBuilder", v).apply()
}