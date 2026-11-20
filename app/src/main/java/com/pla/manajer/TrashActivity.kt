package com.pla.manajer

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.pla.manajer.adapter.TrashAdapter
import com.pla.manajer.databinding.ActivityTrashBinding
import com.pla.manajer.util.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class TrashActivity : AppCompatActivity() {
    private lateinit var b: ActivityTrashBinding
    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        b = ActivityTrashBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.fabBack.setOnClickListener { finish() }

        val dao = (application as PlaApp).database.trashDao()
        val adapter = TrashAdapter(
            onRestore = { t -> lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    val src = File(t.trashPath); val dst = File(t.originalPath)
                    dst.parentFile?.mkdirs()
                    if (src.exists()) FileUtils.move(src, dst)
                }
                dao.remove(t)
                Toast.makeText(this@TrashActivity, "Dipulihkan", Toast.LENGTH_SHORT).show()
            }},
            onDelete = { t -> lifecycleScope.launch {
                withContext(Dispatchers.IO) { FileUtils.deleteRecursive(File(t.trashPath)) }
                dao.remove(t)
                Toast.makeText(this@TrashActivity, "Dihapus permanen", Toast.LENGTH_SHORT).show()
            }}
        )
        b.recycler.layoutManager = LinearLayoutManager(this)
        b.recycler.adapter = adapter
        lifecycleScope.launch { dao.all().collectLatest { adapter.submit(it) } }
    }
}