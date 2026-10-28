package com.estudio.app.wear

import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.WearableListenerService

/** Recibe materias y tareas del teléfono, las guarda y revisa si hay entregas cerca. */
class DatosService : WearableListenerService() {
    override fun onDataChanged(events: DataEventBuffer) {
        for (e in events) {
            if (e.type == DataEvent.TYPE_CHANGED && e.dataItem.uri.path == "/datos") {
                val bytes = e.dataItem.data ?: continue
                Store.guardar(this, String(bytes))
                Avisos.revisar(this)
            }
        }
    }
}
