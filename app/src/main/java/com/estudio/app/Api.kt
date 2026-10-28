package com.estudio.app

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/** Cliente mínimo de Supabase (Auth + REST) sobre OkHttp. Llamar siempre desde Dispatchers.IO. */
object Api {
    private val http = OkHttpClient()
    private val jsonType = "application/json".toMediaType()
    var token: String? = null

    private fun req(path: String, method: String, body: JSONObject? = null): String {
        val rb = Request.Builder()
            .url(BuildConfig.SUPABASE_URL + path)
            .header("apikey", BuildConfig.SUPABASE_KEY)
            .header("Prefer", "return=representation")
            .method(method, body?.toString()?.toRequestBody(jsonType))
        token?.let { rb.header("Authorization", "Bearer $it") }
        http.newCall(rb.build()).execute().use { r ->
            val t = r.body?.string().orEmpty()
            if (!r.isSuccessful) {
                if (r.code == 404) throw Exception("No se encontró la tabla. Ejecuta el SQL en Supabase.")
                val m = runCatching { JSONObject(t).let { it.optString("message", it.optString("msg", t)) } }.getOrDefault(t)
                throw Exception(m)
            }
            return t
        }
    }

    fun auth(email: String, pass: String, signup: Boolean) {
        token = null
        val o = JSONObject().put("email", email).put("password", pass)
        val path = if (signup) "/auth/v1/signup" else "/auth/v1/token?grant_type=password"
        val t = JSONObject(req(path, "POST", o))
        token = t.optString("access_token").ifBlank {
            throw Exception("Cuenta creada. Si pide confirmar correo, confírmalo y entra.")
        }
    }

    fun list(table: String, q: String = "select=*"): List<JSONObject> {
        val a = JSONArray(req("/rest/v1/$table?$q", "GET"))
        return List(a.length()) { a.getJSONObject(it) }
    }

    fun add(table: String, o: JSONObject) { req("/rest/v1/$table", "POST", o) }
    fun update(table: String, id: String, o: JSONObject) { req("/rest/v1/$table?id=eq.$id", "PATCH", o) }
    fun del(table: String, id: String) { req("/rest/v1/$table?id=eq.$id", "DELETE") }
}
