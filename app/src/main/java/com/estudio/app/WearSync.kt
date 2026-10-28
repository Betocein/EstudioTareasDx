package com.estudio.app

import android.content.Context
import android.net.Uri
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import org.json.JSONArray
import org.json.JSONObject

/** Puente teléfono <-> reloj (Wearable Data Layer). Llamar desde Dispatchers.IO. */
object WearSync {
    /** Manda materias y tareas (con su tiempo asignado) al reloj. */
    fun enviarDatos(ctx: Context, materias: List<JSONObject>, tareas: List<JSONObject>) {
        val j = JSONObject().put("t", System.currentTimeMillis())
            .put("materias", JSONArray(materias)).put("tareas", JSONArray(tareas))
        val req = PutDataRequest.create("/datos").setUrgent()
        req.data = j.toString().toByteArray()
        Wearable.getDataClient(ctx).putDataItem(req)
    }

    /** Sube a Supabase lo que hizo el reloj: sesiones de estudio y tareas marcadas como terminadas. */
    fun subirSesiones(ctx: Context): Int {
        val dc = Wearable.getDataClient(ctx)
        var n = procesar(dc, "/sesion") { Api.add("sesiones_estudio", it) }
        n += procesar(dc, "/hecha") { Api.update("tareas", it.getString("id"), JSONObject().put("estado", "entregada")) }
        return n
    }

    private fun procesar(dc: DataClient, ruta: String, accion: (JSONObject) -> Unit): Int {
        val uri = Uri.Builder().scheme(PutDataRequest.WEAR_URI_SCHEME).path(ruta).build()
        val buf = Tasks.await(dc.getDataItems(uri, DataClient.FILTER_PREFIX))
        var n = 0
        try {
            for (item in buf) {
                val bytes = item.data ?: continue
                val o = JSONObject(String(bytes))
                val ok = try { accion(o); n++; true }
                         catch (e: Exception) { (e.message ?: "").contains("duplicate", true) }
                if (ok) Tasks.await(dc.deleteDataItems(item.uri))
            }
        } finally { buf.release() }
        return n
    }
}
