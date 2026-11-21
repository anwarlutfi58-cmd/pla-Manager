package com.pla.manajer

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.pla.manajer.databinding.ActivityCloudBinding

class CloudActivity : AppCompatActivity() {
    private lateinit var b: ActivityCloudBinding
    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        b = ActivityCloudBinding.inflate(layoutInflater)
        setContentView(b.root)
    }
}