package com.estudio.app

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.Wearable
import org.json.JSONObject
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { Surface(Modifier.fillMaxSize()) { App() } } }
    }
}

/** Ejecuta una llamada de red; si falla muestra el error en pantalla y devuelve null. */
suspend fun <T> call(ctx: Context, block: () -> T): T? =
    try { withContext(Dispatchers.IO) { block() } }
    catch (e: Exception) { aviso(ctx, e.message ?: "Error"); null }

fun aviso(ctx: Context, s: String) = Toast.makeText(ctx, s, Toast.LENGTH_LONG).show()

@Composable
fun App() {
    var logged by remember { mutableStateOf(false) }
    if (logged) Home() else Login { logged = true }
}

@Composable
fun Login(onOk: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var err by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val sc = rememberCoroutineScope()
    fun go(signup: Boolean) {
        if (email.isBlank() || pass.length < 6) { err = "Escribe un correo y una contraseña de al menos 6 caracteres"; return }
        sc.launch {
            busy = true; err = ""
            try { withContext(Dispatchers.IO) { Api.auth(email.trim(), pass, signup) }; onOk() }
            catch (e: Exception) { err = e.message ?: "Error" }
            busy = false
        }
    }
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text("Estudio y Tareas", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(email, { email = it }, label = { Text("Correo") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(pass, { pass = it }, label = { Text("Contraseña") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        if (err.isNotEmpty()) Text(err, color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(8.dp))
        Row {
            Button({ go(false) }, enabled = !busy) { Text("Entrar") }
            Spacer(Modifier.width(8.dp))
            OutlinedButton({ go(true) }, enabled = !busy) { Text("Registrarme") }
        }
    }
}

@Composable
fun Home() {
    val ctx = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }
    val materias = remember { mutableStateListOf<JSONObject>() }
    val sc = rememberCoroutineScope()
    fun load() = sc.launch {
        call(ctx) { Api.list("materias", "select=*&order=nombre") }?.let { materias.clear(); materias.addAll(it) }
    }
    LaunchedEffect(Unit) { load() }
    fun syncReloj() = sc.launch {
        withContext(Dispatchers.IO) {
            runCatching {
                WearSync.enviarDatos(ctx,
                    Api.list("materias", "select=*&order=nombre"),
                    Api.list("tareas", "select=*&order=fecha_entrega"))
                WearSync.subirSesiones(ctx)
            }
        }
    }
    LaunchedEffect(tab) { syncReloj() }
    DisposableEffect(Unit) {
        val dc = Wearable.getDataClient(ctx)
        val l = DataClient.OnDataChangedListener { ev ->
            if (ev.any { it.type == DataEvent.TYPE_CHANGED && it.dataItem.uri.path.orEmpty().let { p -> p.startsWith("/sesion") || p.startsWith("/hecha") } })
                sc.launch { withContext(Dispatchers.IO) { runCatching { WearSync.subirSesiones(ctx) } } }
        }
        dc.addListener(l)
        onDispose { dc.removeListener(l) }
    }
    Scaffold(bottomBar = {
        NavigationBar {
            listOf("📚" to "Materias", "📝" to "Tareas", "📊" to "Progreso").forEachIndexed { i, (ic, t) ->
                NavigationBarItem(tab == i, { tab = i }, { Text(ic) }, label = { Text(t) })
            }
        }
    }) { p ->
        Box(Modifier.padding(p).padding(16.dp)) {
            when (tab) {
                0 -> Materias(materias) { load() }
                1 -> Tareas(materias)
                else -> Progreso(materias)
            }
        }
    }
}

@Composable
fun MateriaPicker(materias: List<JSONObject>, sel: JSONObject?, onSel: (JSONObject) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton({ open = true }) { Text(sel?.getString("nombre") ?: "Elegir materia") }
        DropdownMenu(open, { open = false }) {
            if (materias.isEmpty()) DropdownMenuItem({ Text("Primero crea una materia") }, { open = false })
            materias.forEach { m -> DropdownMenuItem({ Text(m.getString("nombre")) }, { onSel(m); open = false }) }
        }
    }
}

@Composable
fun Materias(lista: List<JSONObject>, reload: () -> Unit) {
    val ctx = LocalContext.current
    var n by remember { mutableStateOf("") }
    val sc = rememberCoroutineScope()
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(n, { n = it }, label = { Text("Nueva materia") }, singleLine = true, modifier = Modifier.weight(1f))
            Button({
                if (n.isBlank()) aviso(ctx, "Escribe el nombre de la materia")
                else sc.launch {
                    if (call(ctx) { Api.add("materias", JSONObject().put("nombre", n.trim())) } != null) { n = ""; reload() }
                }
            }, Modifier.padding(start = 8.dp)) { Text("+") }
        }
        if (lista.isEmpty()) Text("Aún no tienes materias.", Modifier.padding(top = 16.dp))
        LazyColumn {
            items(lista) { m ->
                ListItem(
                    headlineContent = { Text(m.getString("nombre")) },
                    trailingContent = {
                        TextButton({
                            sc.launch { if (call(ctx) { Api.del("materias", m.getString("id")) } != null) reload() }
                        }) { Text("Borrar") }
                    })
            }
        }
    }
}

@Composable
fun Tareas(materias: List<JSONObject>) {
    val ctx = LocalContext.current
    val tareas = remember { mutableStateListOf<JSONObject>() }
    var titulo by remember { mutableStateOf("") }
    var fecha by remember { mutableStateOf(LocalDate.now().plusDays(1).toString()) }
    var minutos by remember { mutableStateOf("30") }
    var mat by remember { mutableStateOf<JSONObject?>(null) }
    val sc = rememberCoroutineScope()
    fun load() = sc.launch {
        call(ctx) { Api.list("tareas", "select=*&order=fecha_entrega") }?.let { tareas.clear(); tareas.addAll(it) }
    }
    LaunchedEffect(Unit) { load() }
    fun agregar() {
        val m = mat
        if (titulo.isBlank()) return aviso(ctx, "Escribe el título de la tarea")
        if (m == null) return aviso(ctx, "Elige una materia")
        val f = runCatching { LocalDate.parse(fecha.trim()) }.getOrNull()
            ?: return aviso(ctx, "Fecha inválida. Usa el formato AAAA-MM-DD")
        val min = minutos.toIntOrNull() ?: return aviso(ctx, "Escribe los minutos de estudio")
        sc.launch {
            val o = JSONObject().put("titulo", titulo.trim()).put("materia_id", m.getString("id"))
                .put("fecha_entrega", "${f}T23:59:00-07:00").put("minutos_plan", min)
            if (call(ctx) { Api.add("tareas", o) } != null) { titulo = ""; load() }
        }
    }
    Column {
        OutlinedTextField(titulo, { titulo = it }, label = { Text("Tarea") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row {
            OutlinedTextField(fecha, { fecha = it }, label = { Text("Entrega (AAAA-MM-DD)") }, singleLine = true, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(minutos, { minutos = it.filter(Char::isDigit) }, label = { Text("Min") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.width(90.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            MateriaPicker(materias, mat) { mat = it }
            Spacer(Modifier.width(8.dp))
            Button({ agregar() }) { Text("Asignar") }
        }
        LazyColumn {
            items(tareas) { t ->
                val nombre = materias.firstOrNull { it.getString("id") == t.getString("materia_id") }?.getString("nombre") ?: ""
                ListItem(
                    headlineContent = { Text(t.getString("titulo")) },
                    supportingContent = { Text("$nombre · Entrega: ${t.getString("fecha_entrega").take(10)} · ${t.optInt("minutos_plan")} min") },
                    leadingContent = {
                        Checkbox(t.getString("estado") == "entregada", { ok ->
                            sc.launch {
                                val o = JSONObject().put("estado", if (ok) "entregada" else "pendiente")
                                if (call(ctx) { Api.update("tareas", t.getString("id"), o) } != null) load()
                            }
                        })
                    },
                    trailingContent = {
                        TextButton({ sc.launch { if (call(ctx) { Api.del("tareas", t.getString("id")) } != null) load() } }) { Text("Borrar") }
                    })
            }
        }
    }
}

@Composable
fun Progreso(materias: List<JSONObject>) {
    val ctx = LocalContext.current
    val ses = remember { mutableStateListOf<JSONObject>() }
    val tareas = remember { mutableStateListOf<JSONObject>() }
    LaunchedEffect(Unit) {
        call(ctx) { Api.list("sesiones_estudio") }?.let { ses.clear(); ses.addAll(it) }
        call(ctx) { Api.list("tareas") }?.let { tareas.clear(); tareas.addAll(it) }
    }
    val hechas = tareas.count { it.getString("estado") == "entregada" }
    Column(Modifier.fillMaxWidth()) {
        Text("Progreso", style = MaterialTheme.typography.headlineSmall)
        Text("El cronómetro está en tu reloj; aquí ves el resultado.")
        Spacer(Modifier.height(16.dp))
        Text("Tareas: $hechas entregadas · ${tareas.size - hechas} pendientes", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(16.dp))
        Text("Minutos estudiados por materia", style = MaterialTheme.typography.titleMedium)
        materias.forEach { m ->
            val min = ses.filter { it.getString("materia_id") == m.getString("id") }.sumOf { it.optInt("duracion_min") }
            Text("${m.getString("nombre")}: $min min")
        }
        Text("Total: ${ses.sumOf { it.optInt("duracion_min") }} min", style = MaterialTheme.typography.titleSmall)
    }
}
