package com.pla.manajer

import android.app.AlertDialog
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.EditText
import android.widget.SeekBar
import androidx.recyclerview.widget.RecyclerView
import com.pla.manajer.databinding.ItemMixerTrackBinding

class MixerAdapter(
    private var tracks: MutableList<MixerTrack>,
    private val onDelete: (Int) -> Unit,
    private val onDataChanged: () -> Unit
) : RecyclerView.Adapter<MixerAdapter.VH>() {

    inner class VH(val b: ItemMixerTrackBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemMixerTrackBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(h: VH, position: Int) {
        val track = tracks[position]
        
        h.b.tvMixerFileName.text = track.file.name
        h.b.btnMixerStartTime.text = track.startTime
        
        h.b.seekMixerVol.progress = track.volume
        h.b.tvMixerVolLabel.text = "Volume: ${track.volume}%"
        
        h.b.seekMixerPan.progress = track.panning
        updatePanLabel(h, track.panning)

        // Klik Ubah Waktu
        h.b.btnMixerStartTime.setOnClickListener {
            val dialogView = LayoutInflater.from(h.itemView.context).inflate(R.layout.dialog_time_picker, null)
            val etHours = dialogView.findViewById<EditText>(R.id.etHours)
            val etMinutes = dialogView.findViewById<EditText>(R.id.etMinutes)
            val etSeconds = dialogView.findViewById<EditText>(R.id.etSeconds)
            
            val parts = track.startTime.split(":")
            etHours.setText(parts[0])
            etMinutes.setText(parts[1])
            etSeconds.setText(parts[2])
            
            // Logika Matikan Jam jika file kurang dari 1 jam
            // (Anggap saja kita matikan jika user mau, atau biarkan aktif)
            
            AlertDialog.Builder(h.itemView.context)
                .setTitle("Atur Waktu Mulai")
                .setView(dialogView)
                .setPositiveButton("Simpan") { _, _ ->
                    val hrs = etHours.text.toString().padStart(2, '0').ifEmpty { "00" }
                    val mnt = etMinutes.text.toString().padStart(2, '0').ifEmpty { "00" }
                    val sec = etSeconds.text.toString().padStart(2, '0').ifEmpty { "00" }
                    track.startTime = "$hrs:$mnt:$sec"
                    h.b.btnMixerStartTime.text = track.startTime
                    onDataChanged()
                }
                .setNegativeButton("Batal", null)
                .show()
        }

        // Geser Volume
        h.b.seekMixerVol.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    track.volume = progress
                    h.b.tvMixerVolLabel.text = "Volume: $progress%"
                    onDataChanged()
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        // Geser Panning (Kiri/Kanan)
        h.b.seekMixerPan.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    track.panning = progress
                    updatePanLabel(h, progress)
                    onDataChanged()
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        // Hapus Track
        h.b.btnRemoveTrack.setOnClickListener {
            onDelete(position)
        }
    }

    private fun updatePanLabel(h: VH, pan: Int) {
        h.b.tvMixerPanLabel.text = when {
            pan == 100 -> "Panning: Tengah"
            pan < 100 -> "Panning: Kiri (${100 - pan}%)"
            else -> "Panning: Kanan (${pan - 100}%)"
        }
    }

    override fun getItemCount() = tracks.size
}