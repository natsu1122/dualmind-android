# Validación de la versión inicial

Estado comprobado el 20 de octubre de 2026. Esta versión es una **alpha de desarrollo**, firmada con certificado debug. No se ha conectado un Xiaomi 15T al entorno de desarrollo.

## Comprobaciones realizadas

| Comprobación | Resultado observado |
|---|---|
| `:core:test` | 18 pruebas, 0 fallos: orquestación, rondas, cancelación, concurrencia, contexto y copias cifradas |
| `:app:testDebugUnitTest` | 5 pruebas Robolectric/SQLite, 0 fallos: persistencia, recuperación, FTS, identidades y restauración |
| `:app:assembleDebug` | APK ARM64 generado correctamente |
| `:app:assembleDebugAndroidTest` | APK de instrumentación generado correctamente |
| `:app:lintDebug` | Sin errores; avisos de versiones nuevas, ABI de ChromeOS y recomendaciones menores |
| Qwen3-1.7B en LiteRT-LM JVM 0.18.0 / CPU / Java 21 | Inferencia real: «Dos más dos es cuatro.» |
| SQLCipher + Android Keystore, API 35 x86_64 | Prueba instrumentada correcta: misma clave, base reabierta y contenido ausente en texto plano |
| Navegación Compose, API 35 x86_64 | Prueba instrumentada correcta de las seis pantallas y creación explícita de recuerdo |
| Debate nativo Qwen, emulador de 4 GiB | Android terminó el proceso por `LOW_MEMORY`; no cuenta como inferencia correcta |

El archivo de Qwen usado en la prueba coincide con el SHA-256 publicado en `MODELS.md`. Los pesos permanecen fuera del repositorio. La prueba JVM demuestra una ejecución real de ese archivo en escritorio; no certifica la variante Android, Gemma ni el hardware Xiaomi.

## Pendiente de validación

- Inferencia Android: repetir la prueba nativa con más RAM; el emulador inicial de 4 GiB no fue suficiente.
- Ejecución de GitHub Actions sobre el código publicado y verificación del APK de entrega.
- Gemma3-1B-IT: requiere descarga por el usuario tras aceptar sus condiciones. No se descargó ni se probó.
- Dos familias distintas de modelos en un debate real: Qwen + Gemma.
- Xiaomi 15T: instalación ARM64, importación SAF, aceleración GPU, carga térmica, batería, memoria bajo presión y latencias reales.
- Accesibilidad con TalkBack, tamaño de fuente máximo y versiones Android diferentes al emulador.

No se afirma soporte NPU, rendimiento del Xiaomi, cifrado de pesos ni búsqueda vectorial. La recuperación usa FTS4. La cancelación depende de la cooperación del runtime nativo; una inicialización bloqueante puede superar el plazo nominal. Los pasos físicos reproducibles están en [DEVICE_TEST_PLAN.md](DEVICE_TEST_PLAN.md).
