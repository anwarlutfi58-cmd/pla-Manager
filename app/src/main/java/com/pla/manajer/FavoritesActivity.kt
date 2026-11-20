package com.pla.manajer

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.pla.manajer.adapter.FavoriteAdapter
import com.pla.manajer.databinding.ActivityFavoritesBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File

class FavoritesActivity : AppCompatActivity() {
    private lateinit var b: ActivityFavoritesBinding
    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        b = ActivityFavoritesBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.fabBack.setOnClickListener { finish() }

        val adapter = FavoriteAdapter(
            onOpen = { f ->
                val i = if (File(f.path).isDirectory) Intent(this, FileManagerActivity::class.java)
                        else Intent(this, CodeEditorActivity::class.java).putExtra("path", f.path)
                startActivity(i)
            },
            onRemove = { f ->
                lifecycleScope.launch { (application as PlaApp).database.favoriteDao().remove(f.path) }
            }
        )
        b.recycler.layoutManager = LinearLayoutManager(this)
        b.recycler.adapter = adapter
        lifecycleScope.launch {
            (application as PlaApp).database.favoriteDao().all().collectLatest { adapter.submit(it) }
        }
    }
}