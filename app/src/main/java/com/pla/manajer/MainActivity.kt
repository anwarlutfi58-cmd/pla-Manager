package com.pla.manajer

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.pla.manajer.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var b: ActivityMainBinding
    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.cardStorage.setOnClickListener   { startActivity(Intent(this, FileManagerActivity::class.java)) }
        b.cardProjects.setOnClickListener  { startActivity(Intent(this, ProjectManagerActivity::class.java)) }
        b.cardFavorites.setOnClickListener { startActivity(Intent(this, FavoritesActivity::class.java)) }
        b.cardTrash.setOnClickListener     { startActivity(Intent(this, TrashActivity::class.java)) }
        b.cardSettings.setOnClickListener  { startActivity(Intent(this, SettingsActivity::class.java)) }
    }
}