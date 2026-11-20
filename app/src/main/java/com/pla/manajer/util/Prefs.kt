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
}