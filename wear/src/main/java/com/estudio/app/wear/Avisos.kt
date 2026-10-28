package com.estudio.app.wear

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.time.OffsetDateTime

object Avisos {
    /** Notifica con vibración las tareas pendientes que vencen en las próximas 24 horas. */
    @SuppressLint("MissingPermission")
    fun revisar(c: Context) {
        if (Build.VERSION.SDK_INT >= 33 &&
            c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val canal = NotificationChannel("entregas", "Entregas", NotificationManager.IMPORTANCE_HIGH)
            .apply { enableVibration(true) }
        c.getSystemService(NotificationManager::class.java).createNotificationChannel(canal)
        val mats = Store.lista(c, "materias").associate { it.getString("id") to it.getString("nombre") }
        val ahora = OffsetDateTime.now()
        val limite = ahora.plusHours(24)
        Store.lista(c, "tareas").filter {
            val f = runCatching { OffsetDateTime.parse(it.getString("fecha_entrega")) }.getOrNull()
            it.getString("estado") != "entregada" && f != null && f.isAfter(ahora) && f.isBefore(limite)
        }.forEach { t ->
            val n = NotificationCompat.Builder(c, "entregas")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Entrega próxima")
                .setContentText("${t.getString("titulo")} · ${mats[t.getString("materia_id")] ?: ""}")
                .setVibrate(longArrayOf(0, 300, 150, 300))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()
            NotificationManagerCompat.from(c).notify(t.getString("id").hashCode(), n)
        }
    }
}
