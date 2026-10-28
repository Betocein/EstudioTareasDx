package com.estudio.app.wear

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

/** Copia local (en el reloj) de materias y tareas enviadas por el teléfono, y de la sesión en curso. */
object Store {
    private fun p(c: Context) = c.getSharedPreferences("datos", Context.MODE_PRIVATE)
    fun guardar(c: Context, json: String) = p(c).edit().putString("json", json).apply()

    fun lista(c: Context, k: String): List<JSONObject> {
        val a = JSONObject(p(c).getString("json", null) ?: "{}").optJSONArray(k) ?: JSONArray()
        return List(a.length()) { a.getJSONObject(it) }
    }

    fun marcarHecha(c: Context, id: String) {
        val r = JSONObject(p(c).getString("json", null) ?: return)
        val a = r.optJSONArray("tareas") ?: return
        for (i in 0 until a.length()) {
            val t = a.getJSONObject(i)
            if (t.getString("id") == id) t.put("estado", "entregada")
        }
        guardar(c, r.toString())
    }

    /** La sesión en curso sobrevive aunque el reloj cierre la app. */
    fun setActiva(c: Context, tareaId: String?, inicio: Instant?) {
        val e = p(c).edit()
        if (tareaId == null || inicio == null) e.remove("activa") else e.putString("activa", "$tareaId|$inicio")
        e.apply()
    }

    fun activa(c: Context): Pair<String, Instant>? {
        val s = p(c).getString("activa", null)?.split("|") ?: return null
        return if (s.size == 2) s[0] to Instant.parse(s[1]) else null
    }
}
