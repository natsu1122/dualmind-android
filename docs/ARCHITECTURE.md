# Arquitectura

`core` no depende de Android. `DebateOrchestrator` serializa ejecuciones con `Mutex.tryLock`, emite eventos y pide una generación por intervención a `InferenceEngine`. Los modelos y las identidades son instantáneas de la solicitud. El modo profundo tiene un límite fijo configurable; no finge detectar consenso.

`LiteRtEngine` mantiene un mutex propio, carga en `Dispatchers.IO`, crea la conversación y consume callbacks del runtime. La finalización usa `NonCancellable` para esperar la terminación antes de cerrar conversación y motor. No se lanza una segunda inferencia hasta terminar la anterior. El presupuesto de tokens se pasa a `EngineConfig.maxNumTokens` y `ConversationConfig.maxOutputToken`; el techo de caracteres puede solicitar cancelación anticipada. El tiempo máximo es cooperativo; no puede interrumpir a la fuerza una llamada JNI de carga.

Las identidades se anteponen al contenido de usuario porque algunas plantillas Gemma no admiten rol system. Se deshabilitan herramientas automáticas y thinking. No se exponen herramientas de red, shell o archivos a los modelos.

`MainViewModel` conserva una operación activa, administra Flow/StateFlow y persiste eventos en Room. Se hace un punto de control de streaming aproximadamente cada 500 ms. La muerte del proceso puede perder ese último fragmento. La pantalla Debate conserva los estados incompletos y las intervenciones ya terminadas. Cambiar de configuración o modelo no modifica las conversaciones almacenadas.

La base cifrada contiene chats, intervenciones, ejecuciones, memorias, índice FTS, proyectos, identidades, modelos y asignaciones. DataStore contiene solo preferencias de rendimiento, tema y guardado. Los modelos permanecen en archivos internos. Las copias incluyen datos portables, no rutas ejecutables ni archivos de modelos.

No hay WorkManager: no existen descargas ni trabajos persistentes que justifiquen su uso. La inferencia se vincula al ViewModel de la actividad y no garantiza continuar en segundo plano. No hay servicio foreground que mantenga el teléfono despierto.

## Contexto

1. Tokenizar la consulta en términos seguros para FTS, sin permitir sintaxis arbitraria.
2. Recuperar coincidencias globales y del proyecto actual; excluir otros proyectos.
3. Añadir recuerdos recientes del proyecto y deduplicar.
4. Seleccionar hasta ocho entradas con presupuesto de 500 bytes.
5. Repartir el presupuesto de cada ronda entre pregunta, identidad, propuesta/crítica, recuerdos e historial.
6. Registrar los recuerdos efectivamente insertados en esa ronda.

El cálculo UTF-8 es deliberadamente conservador para los tokenizadores con byte fallback de los modelos previstos; no se presenta como conteo exacto del tokenizador. El runtime impone el límite real. Preguntas o identidades excesivas se rechazan con un mensaje.

## Métricas

Tiempo total y carga: reloj monotónico Android. Tiempo al primer texto visible: primer callback no vacío desde el inicio de la carga. PSS: muestra del proceso al finalizar; no es el pico de RAM ni consumo exclusivo del modelo. Tokens/segundo y tokens de salida: datos de `getBenchmarkInfo`, si el runtime los ofrece. Los fragmentos de streaming nunca se cuentan como tokens. No se mide energía ni temperatura automáticamente.

## Aceptación

Las pruebas locales automatizadas deben cubrir A/B/A, límites, cancelación, fallos, bloqueo de duplicados, cambios de modelo/identidad, FTS, reapertura de la base y copias autenticadas. La validación completa requiere instalar el APK, ejecutar los dos archivos reales, reiniciar Android y probar modo avión. El estado real se publica en `VALIDATION.md`.

La base, el motor y el bloqueo de operaciones pertenecen al proceso de Application. Reabrir la actividad no crea otro motor. Antes de cargar se exige RAM libre superior a 3 veces el tamaño del archivo más 512 MiB, con mínimo de 1 GiB. Es una estimación conservadora de admisión, no una garantía frente a picos nativos ni al cierre por Android.

