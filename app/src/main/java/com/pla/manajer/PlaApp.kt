package com.pla.manajer

import android.app.Application
import com.pla.manajer.data.AppDatabase

class PlaApp : Application() {
    val database by lazy { AppDatabase.get(this) }
}