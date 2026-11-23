package com.pla.manajer

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.google.android.material.tabs.TabLayout
import com.pla.manajer.databinding.ActivityVideoAdvancedBinding
import com.pla.manajer.util.NotificationHelper
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

class VideoAdvancedActivity : AppCompatActivity() {
    private lateinit var b: ActivityVideoAdvancedBinding
    private lateinit var notif: NotificationHelper
    private val imageUris = mutableListOf<Uri>()
    private var audioUri: Uri? = null

    private val pickImages = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == RESULT_OK) {
            val d = r.data
            if (d?.clipData != null) {
                val c = d.clipData!!
                for (i in 0 until c.itemCount) imageUris.add(c.getItemAt(i).uri)
            } else if (d?.data != null) imageUris.add(d.data!!)
            Toast.makeText(this, "${imageUris.size} gambar dipilih", Toast.LENGTH_SHORT).show()
        }
    }

    private val pickAudio = registerForActivityResult(ActivityResultContracts.GetContent()) { u ->
        if (u != null) {
            audioUri = u
            b.tvAudioSelected.text = getFileName(u)
        }
    }

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        b = ActivityVideoAdvancedBinding.inflate(layoutInflater)
        setContentView(b.root)
        notif = NotificationHelper(this)

        b.btnBack.setOnClickListener { finish() }

        b.spinnerTransisi.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,
            arrayOf("Tanpa Transisi", "Fade", "Slide", "Wipe", "Zoom"))
        b.spinnerPreset.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,
            arrayOf("Custom", "YouTube", "TikTok", "Instagram", "WhatsApp"))
        b.spinnerResolusi.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,
            arrayOf("400p (640x360)", "720p (1280x720)", "1080p (1920x1080)"))
        b.spinnerResolusi.setSelection(1)
        b.spinnerBitrate.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,
            arrayOf("2M", "4M", "8M", "16M"))
        b.spinnerBitrate.setSelection(1)
        b.spinnerCodec.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,
            arrayOf("H.264", "H.265"))
        b.spinnerFps.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,
            arrayOf("24", "30", "60"))
        b.spinnerFps.setSelection(1)

        b.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                b.panelTransisi.visibility = View.GONE
                b.panelTeks.visibility = View.GONE
                b.panelAudio.visibility = View.GONE
                b.panelExport.visibility = View.GONE
                when (tab?.position) {
                    0 -> b.panelTransisi.visibility = View.VISIBLE
                    1 -> b.panelTeks.visibility = View.VISIBLE
                    2 -> b.panelAudio.visibility = View.VISIBLE
                    3 -> b.panelExport.visibility = View.VISIBLE
                }
            }
            override fun onTabUnselected(t: TabLayout.Tab?) {}
            override fun onTabReselected(t: TabLayout.Tab?) {}
        })

        b.seekTransisiDurasi.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, f: Boolean) {
                b.tvTransisiDurasi.text = "Durasi Transisi: ${"%.1f".format(p / 10f)} detik"
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
        b.seekKenBurns.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, f: Boolean) {
                b.tvKenBurns.text = "Intensitas Ken Burns: $p%"
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
        b.seekDucking.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, f: Boolean) {
                b.tvDucking.text = "Intensitas Ducking: $p%"
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        b.spinnerPreset.setOnItemSelectedListener(object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                when (pos) {
                    1 -> { b.spinnerResolusi.setSelection(2); b.spinnerBitrate.setSelection(2); b.spinnerFps.setSelection(1) }
                    2 -> { b.spinnerResolusi.setSelection(2); b.spinnerBitrate.setSelection(1); b.spinnerFps.setSelection(1) }
                    3 -> { b.spinnerResolusi.setSelection(2); b.spinnerBitrate.setSelection(1); b.spinnerFps.setSelection(1) }
                    4 -> { b.spinnerResolusi.setSelection(1); b.spinnerBitrate.setSelection(0); b.spinnerFps.setSelection(1) }
                }
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        })

        b.btnPickImages.setOnClickListener {
            val i = Intent(Intent.ACTION_GET_CONTENT)
            i.type = "image/*"
            i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            pickImages.launch(i)
        }
        b.btnPickAudio.setOnClickListener { pickAudio.launch("audio/*") }
        b.btnProses.setOnClickListener { onProses() }
    }

    private fun getFileName(u: Uri): String {
        var n = "Audio"
        contentResolver.query(u, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (c.moveToFirst()) n = c.getString(idx)
        }
        return n
    }

    private fun onProses() {
        if (imageUris.isEmpty()) { Toast.makeText(this, "Pilih gambar dulu", Toast.LENGTH_SHORT).show(); return }

        val resolution = when (b.spinnerResolusi.selectedItemPosition) {
            0 -> "640:360"; 2 -> "1920:1080"; else -> "1280:720"
        }
        val (rw, rh) = resolution.split(":").map { it.toInt() }
        val fps = b.spinnerFps.selectedItem.toString()
        val bitrate = b.spinnerBitrate.selectedItem.toString()
        val codec = if (b.spinnerCodec.selectedItemPosition == 1 && Build.VERSION.SDK_INT >= 24) "libx265" else "libx264"

        val transisiPos = b.spinnerTransisi.selectedItemPosition
        val transisiDur = b.seekTransisiDurasi.progress / 10f
        val kenBurns = b.cbKenBurns.isChecked
        val kenBurnsIntensity = b.seekKenBurns.progress / 100f

        val useBpm = b.cbUseBpm.isChecked
        val bpmVal = b.etBpm.text.toString().toIntOrNull() ?: 120
        val perGambar = if (useBpm) (60f / bpmVal) * 2f else (b.etDurasiGambar.text.toString().toFloatOrNull() ?: 3f)

        val judul = if (b.cbTeksJudul.isChecked) b.etTeksJudul.text.toString().trim().ifEmpty { null } else null
        val teksAnimasi = b.cbTeksAnimasi.isChecked
        val subtitleLines = if (b.cbSubtitle.isChecked) b.etSubtitle.text.toString().split("\n").filter { it.isNotBlank() } else emptyList()
        val sticker = if (b.cbSticker.isChecked) b.etSticker.text.toString().trim().ifEmpty { null } else null
        val watermark = if (b.cbWatermark.isChecked) b.etWatermark.text.toString().trim().ifEmpty { null } else null
        val marquee = if (b.cbMarquee.isChecked) b.etMarquee.text.toString().trim().ifEmpty { null } else null

        val ducking = b.cbDucking.isChecked
        val duckIntensity = b.seekDucking.progress / 100f
        val normalize = b.cbNormalize.isChecked

        val exportSrt = b.cbExportSrt.isChecked
        val thumbTime = b.etThumbnailWaktu.text.toString().trim()

        val outDir = File(Environment.getExternalStorageDirectory(), "PlaProjects/Edit_Video").apply { mkdirs() }
        val stamp = System.currentTimeMillis()
        val outFile = File(outDir, "ADV_${stamp}.mp4")

        b.btnProses.isEnabled = false
        b.tvProgress.text = "Menyiapkan..."
        notif.showEdit("Video Advanced", "Menyiapkan pipeline...")

        try {
            // Salin gambar ke cache
            val localImgs = mutableListOf<File>()
            imageUris.forEachIndexed { i, u ->
                val f = File(cacheDir, "adv_img_${i}_${System.currentTimeMillis()}.jpg")
                contentResolver.openInputStream(u)?.use { inp -> FileOutputStream(f).use { inp.copyTo(it) } }
                localImgs.add(f)
            }

            // Salin audio ke cache
            var localAudio: File? = null
            audioUri?.let { u ->
                val f = File(cacheDir, "adv_bgm_${System.currentTimeMillis()}.m4a")
                contentResolver.openInputStream(u)?.use { inp -> FileOutputStream(f).use { inp.copyTo(it) } }
                localAudio = f
            }

            val totalDur = perGambar * localImgs.size
            val cmd = buildCommand(
                images = localImgs, audio = localAudio,
                transitionPos = transisiPos, transitionDur = transisiDur,
                kenBurns = kenBurns, kenBurnsIntensity = kenBurnsIntensity,
                perImageDur = perGambar, totalDur = totalDur,
                resolution = resolution, rw = rw, rh = rh, fps = fps,
                bitrate = bitrate, codec = codec,
                judul = judul, teksAnimasi = teksAnimasi,
                subtitleLines = subtitleLines,
                sticker = sticker, watermark = watermark, marquee = marquee,
                ducking = ducking, duckIntensity = duckIntensity, normalize = normalize,
                outputFile = outFile
            )

            notif.showEdit("Video Advanced", "Merender video...")
            b.tvProgress.text = "Merender video..."

            FFmpegKit.executeAsync(cmd, { session ->
                runOnUiThread {
                    b.btnProses.isEnabled = true
                    notif.hideEdit()
                    if (ReturnCode.isSuccess(session.returnCode)) {
                        registerVideo(outFile)
                        // Thumbnail
                        if (thumbTime.isNotEmpty()) extractThumbnail(outFile, thumbTime, outDir)
                        // SRT
                        if (exportSrt && subtitleLines.isNotEmpty()) writeSrt(outDir, stamp, subtitleLines, perGambar)
                        b.tvProgress.text = "Selesai: ${outFile.name}"
                        Toast.makeText(this, "Video berhasil dibuat", Toast.LENGTH_LONG).show()
                        finish()
                    } else {
                        val err = session.failStackTrace ?: session.allLogsAsString ?: "unknown"
                        b.tvProgress.text = "Gagal: ${err.take(200)}"
                        Toast.makeText(this, "Gagal: ${err.take(300)}", Toast.LENGTH_LONG).show()
                    }
                }
            }, { }, { })
        } catch (e: Exception) {
            b.btnProses.isEnabled = true
            notif.hideEdit()
            b.tvProgress.text = "Error: ${e.message}"
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun buildCommand(
        images: List<File>, audio: File?,
        transitionPos: Int, transitionDur: Float,
        kenBurns: Boolean, kenBurnsIntensity: Float,
        perImageDur: Float, totalDur: Float,
        resolution: String, rw: Int, rh: Int, fps: String,
        bitrate: String, codec: String,
        judul: String?, teksAnimasi: Boolean,
        subtitleLines: List<String>,
        sticker: String?, watermark: String?, marquee: String?,
        ducking: Boolean, duckIntensity: Float, normalize: Boolean,
        outputFile: File
    ): String {
        val cmd = StringBuilder("-y ")

        // Apakah perlu filter_complex?
        val needFilter = transitionPos != 0 || kenBurns || judul != null ||
                subtitleLines.isNotEmpty() || sticker != null || watermark != null || marquee != null

        if (!needFilter) {
            // Simple concat demuxer
            val listFile = File(cacheDir, "adv_concat_${System.currentTimeMillis()}.txt")
            listFile.bufferedWriter().use { w ->
                images.forEachIndexed { i, f ->
                    w.write("file '${f.absolutePath}'\n")
                    w.write("duration ${perImageDur}\n")
                    if (i == images.size - 1) w.write("file '${f.absolutePath}'\n")
                }
            }
            cmd.append("-f concat -safe 0 -i \"${listFile.absolutePath}\" ")
            if (audio != null) cmd.append("-i \"${audio.absolutePath}\" ")
            cmd.append("-vf scale=$rw:$rh,setsar=1,fps=$fps ")
            cmd.append("-c:v $codec -b:v $bitrate -pix_fmt yuv420p ")
            if (audio != null) {
                val af = StringBuilder()
                if (ducking) af.append("volume=${1.0f - duckIntensity},")
                if (normalize) af.append("loudnorm=I=-14:TP=-1:LRA=11,")
                af.append("afade=t=in:st=0:d=1,afade=t=out:st=${totalDur - 1}:d=1")
                cmd.append("-af \"${af}\" -c:a aac -shortest ")
            }
            cmd.append("\"${outputFile.absolutePath}\"")
            return cmd.toString()
        }

        // Filter complex pipeline
        images.forEach { cmd.append("-loop 1 -t $perImageDur -i \"${it.absolutePath}\" ") }
        var audioInputIdx = -1
        if (audio != null) {
            audioInputIdx = images.size
            cmd.append("-i \"${audio.absolutePath}\" ")
        }

        // Prepare overlay text PNGs
        val overlays = mutableListOf<Pair<File, String>>() // file + enable expr
        if (judul != null) {
            val f = renderText(judul, rw, rh, rh / 8f, Color.WHITE, rw / 2f, rh / 4f, bold = true)
            overlays.add(f to "between(t,0,${totalDur})")
        }
        if (subtitleLines.isNotEmpty()) {
            subtitleLines.forEachIndexed { i, line ->
                if (i < images.size) {
                    val st = i * perImageDur
                    val en = st + perImageDur
                    val f = renderText(line, rw, rh, rh / 18f, Color.WHITE, rw / 2f, (rh * 0.85f), bold = false)
                    overlays.add(f to "between(t,$st,$en)")
                }
            }
        }
        if (sticker != null) {
            val f = renderText(sticker, rw, rh, rh / 10f, Color.WHITE, rw * 0.85f, rh * 0.15f, bold = true)
            overlays.add(f to "between(t,0,${totalDur})")
        }
        if (watermark != null) {
            val f = renderText(watermark, rw, rh, rh / 30f, Color.argb(180, 255, 255, 255), rw * 0.9f, rh * 0.95f, bold = false)
            overlays.add(f to "between(t,0,${totalDur})")
        }
        if (marquee != null) {
            val f = renderText(marquee, rw, rh, rh / 22f, Color.YELLOW, rw / 2f, rh * 0.92f, bold = true)
            overlays.add(f to "between(t,0,${totalDur})")
        }
        overlays.forEach { cmd.append("-i \"${it.first.absolutePath}\" ") }

        // Build filter_complex
        val fc = StringBuilder()

        // Per image: scale + optional zoompan
        images.forEachIndexed { i, _ ->
            fc.append("[$i:v]scale=$rw:$rh:force_original_aspect_ratio=decrease,pad=$rw:$rh:(ow-iw)/2:(oh-ih)/2,setsar=1,fps=$fps")
            if (kenBurns) {
                val z = "min(zoom+${0.0005f + kenBurnsIntensity * 0.002f},1.${(10 + kenBurnsIntensity * 30).toInt()})"
                fc.append(",zoompan=z='$z':d=${(perImageDur * fps.toInt()).toInt()}:s=${rw}x${rh}:fps=$fps")
            }
            fc.append("[v$i];")
        }

        // Combine: transitions or concat
        var lastLabel = "v0"
        if (transitionPos != 0 && images.size > 1) {
            val tName = when (transitionPos) { 1 -> "fade"; 2 -> "slideleft"; 3 -> "wipeleft"; else -> "zoomin" }
            var accumulated = 0f
            for (i in 1 until images.size) {
                val outLabel = if (i == images.size - 1) "vcat" else "vx$i"
                val offset = (perImageDur * i) - (transitionDur * i)
                fc.append("[$lastLabel][v$i]xfade=transition=$tName:duration=$transitionDur:offset=$offset[$outLabel];")
                lastLabel = outLabel
            }
        } else {
            images.indices.forEach { fc.append("[v$it]") }
            fc.append("concat=n=${images.size}:v=1:a=0[vcat];")
            lastLabel = "vcat"
        }

        // Overlay text PNGs
        var mainLabel = lastLabel
        overlays.forEachIndexed { idx, (_, enableExpr) ->
            val overlayIdx = images.size + (if (audio != null) 1 else 0) + idx
            val outLabel = if (idx == overlays.size - 1) "vout" else "vo$idx"
            fc.append("[$mainLabel][$overlayIdx:v]overlay=0:0:enable='$enableExpr'[$outLabel];")
            mainLabel = outLabel
        }
        if (overlays.isEmpty()) { fc.append("[$mainLabel]null[vout];"); mainLabel = "vout" }

        cmd.append("-filter_complex \"${fc.toString().trimEnd(';')}\" ")
        cmd.append("-map \"[$mainLabel]\" ")
        if (audio != null) {
            cmd.append("-map $audioInputIdx:a ")
            val af = StringBuilder()
            if (ducking) af.append("volume=${1.0f - duckIntensity},")
            if (normalize) af.append("loudnorm=I=-14:TP=-1:LRA=11,")
            af.append("afade=t=in:st=0:d=1,afade=t=out:st=${totalDur - 1}:d=1")
            cmd.append("-af \"${af}\" -c:a aac -shortest ")
        }
        cmd.append("-c:v $codec -b:v $bitrate -pix_fmt yuv420p -t $totalDur ")
        cmd.append("\"${outputFile.absolutePath}\"")
        return cmd.toString()
    }

    private fun renderText(text: String, w: Int, h: Int, size: Float, color: Int, x: Float, y: Float, bold: Boolean): File {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint().apply {
            this.color = color
            textSize = size
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            setShadowLayer(6f, 3f, 3f, Color.BLACK)
        }
        c.drawText(text, x, y, p)
        val f = File(cacheDir, "text_${System.currentTimeMillis()}_${text.hashCode()}.png")
        FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
        return f
    }

    private fun writeSrt(dir: File, stamp: Long, lines: List<String>, durPer: Float) {
        try {
            val f = File(dir, "ADV_${stamp}.srt")
            var t = 0f
            f.bufferedWriter().use { w ->
                lines.forEachIndexed { i, line ->
                    val st = t; val en = t + durPer
                    w.write("${i + 1}\n")
                    w.write("${srtTime(st)} --> ${srtTime(en)}\n")
                    w.write("$line\n\n")
                    t = en
                }
            }
        } catch (_: Exception) {}
    }

    private fun srtTime(sec: Float): String {
        val h = (sec / 3600).toInt()
        val m = ((sec % 3600) / 60).toInt()
        val s = (sec % 60).toInt()
        val ms = ((sec - sec.toInt()) * 1000).toInt()
        return String.format(Locale.US, "%02d:%02d:%02d,%03d", h, m, s, ms)
    }

    private fun extractThumbnail(video: File, timeStr: String, dir: File) {
        try {
            val f = File(dir, "ADV_${video.nameWithoutExtension}_thumb.jpg")
            val cmd = "-y -ss $timeStr -i \"${video.absolutePath}\" -frames:v 1 \"${f.absolutePath}\""
            FFmpegKit.executeAsync(cmd, { }, { }, { })
        } catch (_: Exception) {}
    }

    private fun registerVideo(file: File) {
        try {
            val v = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, file.name)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                    put(MediaStore.Video.Media.RELATIVE_PATH, "PlaProjects/Edit_Video")
                else put(MediaStore.Video.Media.DATA, file.absolutePath)
            }
            contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, v)
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        super.onDestroy()
        notif.hideEdit()
    }
}