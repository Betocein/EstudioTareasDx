package com.estudio.app.wear

import android.Manifest
import android.content.Context
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.time.Duration
import java.time.Instant
import java.util.UUID

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { Avisos.revisar(this) }
            .launch(Manifest.permission.POST_NOTIFICATIONS)
        setContent { Pantalla() }
    }
}

/** Las sesiones y las tareas terminadas se guardan como DataItem: Data Layer las entrega al teléfono cuando haya conexión. */
fun enviarSesion(ctx: Context, t: JSONObject, inicio: Instant) {
    val fin = Instant.now()
    val seg = Duration.between(inicio, fin).seconds
    val id = UUID.randomUUID().toString()
    val req = PutDataRequest.create("/sesion/$id").setUrgent()
    req.data = JSONObject().put("id", id).put("materia_id", t.getString("materia_id"))
        .put("tarea_id", t.getString("id"))
        .put("inicio", inicio.toString()).put("fin", fin.toString())
        .put("duracion_min", ((seg + 59) / 60).toInt()).put("origen", "reloj")
        .toString().toByteArray()
    Wearable.getDataClient(ctx).putDataItem(req)
}

fun enviarHecha(ctx: Context, tareaId: String) {
    val req = PutDataRequest.create("/hecha/$tareaId").setUrgent()
    req.data = JSONObject().put("id", tareaId).toString().toByteArray()
    Wearable.getDataClient(ctx).putDataItem(req)
}

fun vibrar(ctx: Context) {
    ctx.getSystemService(Vibrator::class.java)
        ?.vibrate(VibrationEffect.createOneShot(600, VibrationEffect.DEFAULT_AMPLITUDE))
}

@Composable
fun Pantalla() {
    val ctx = LocalContext.current
    val materias = remember { Store.lista(ctx, "materias") }
    val tareas = remember {
        mutableStateListOf<JSONObject>().apply {
            addAll(Store.lista(ctx, "tareas").filter { it.getString("estado") != "entregada" }
                .sortedBy { it.getString("fecha_entrega") })
        }
    }
    val prev = remember { Store.activa(ctx) }
    var activa by remember { mutableStateOf(prev?.let { (id, _) -> tareas.firstOrNull { it.getString("id") == id } }) }
    var inicio by remember { mutableStateOf(if (activa != null) prev?.second else null) }
    var seg by remember { mutableLongStateOf(0L) }
    var msg by remember { mutableStateOf("") }
    var avisada by remember { mutableStateOf(false) }
    val a = activa
    val plan = a?.optInt("minutos_plan", 0) ?: 0

    LaunchedEffect(inicio) {
        while (inicio != null) {
            seg = Duration.between(inicio!!, Instant.now()).seconds
            if (plan > 0 && seg >= plan * 60L && !avisada) { avisada = true; vibrar(ctx) }
            delay(1000)
        }
    }

    fun limpiar() { inicio = null; activa = null; seg = 0; avisada = false; Store.setActiva(ctx, null, null) }
    fun cerrar(hecha: Boolean) {
        val i = inicio
        if (a != null && i != null) {
            enviarSesion(ctx, a, i)
            if (hecha) {
                val id = a.getString("id")
                enviarHecha(ctx, id); Store.marcarHecha(ctx, id)
                tareas.removeAll { it.getString("id") == id }
            }
            msg = if (hecha) "¡Tarea terminada!" else "Sesión guardada"
        }
        limpiar()
    }
    fun nombre(t: JSONObject) =
        materias.firstOrNull { it.getString("id") == t.getString("materia_id") }?.getString("nombre") ?: ""

    MaterialTheme {
        ScalingLazyColumn(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            if (a == null) {
                item { Text("Mis tareas", style = MaterialTheme.typography.title3) }
                if (msg.isNotEmpty()) item { Text(msg) }
                if (tareas.isEmpty()) item { Text("Sin tareas. Asígnalas desde el teléfono") }
                items(tareas.size) { n ->
                    val t = tareas[n]
                    val min = t.optInt("minutos_plan", 0)
                    Chip(
                        label = { Text(t.getString("titulo")) },
                        secondaryLabel = { Text(nombre(t) + if (min > 0) " · $min min" else "") },
                        onClick = {
                            val ahora = Instant.now()
                            activa = t; inicio = ahora; seg = 0; msg = ""; avisada = false
                            Store.setActiva(ctx, t.getString("id"), ahora)
                        },
                        colors = ChipDefaults.secondaryChipColors())
                }
            } else {
                item { Text(a.getString("titulo"), style = MaterialTheme.typography.title3) }
                item { Text("%02d:%02d".format(seg / 60, seg % 60), style = MaterialTheme.typography.display1) }
                if (plan > 0) item { Text(if (seg >= plan * 60L) "¡Meta cumplida!" else "Meta: $plan min") }
                item { Chip(label = { Text("Terminé de estudiar") }, onClick = { cerrar(false) }) }
                item { Chip(label = { Text("Tarea terminada ✓") }, onClick = { cerrar(true) }) }
                item { Chip(label = { Text("Cancelar") }, onClick = { limpiar() }, colors = ChipDefaults.secondaryChipColors()) }
            }
        }
    }
}
