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
| Debate nativo Qwen, API 35 x86_64, 6 GiB | Correcto: tres cargas secuenciales A/B/A, tres respuestas no vacías, CPU, Wi-Fi y datos desactivados |
| Revisión visual | Seis capturas reales del emulador inspeccionadas en modo claro; sin solapamientos de controles observados |
| Manifiesto fusionado debug | Sin permiso `INTERNET`; copias automáticas deshabilitadas y exclusiones explícitas de transferencia |

El archivo de Qwen usado en las pruebas coincide con el SHA-256 publicado en `MODELS.md`. Los pesos permanecen fuera del repositorio. En Android, ambos roles usaron **el mismo archivo Qwen**, con identidades distintas. Esto verifica inferencia y orquestación real A/B/A; no certifica dos familias diferentes ni Gemma.

La suite nativa finalizó `OK (1 test)` en 126,643 segundos, incluyendo copia y hash del modelo. Las pruebas separadas de almacenamiento y UI finalizaron `OK (2 tests)`; la UI se repitió correctamente después de centralizar los recursos por proceso.

### Mediciones del emulador, no del Xiaomi

Contexto 2.048, máximo 64 tokens por intervención, cuatro hilos CPU, LiteRT-LM 0.18.0. El emulador tenía 6 GiB configurados. Los valores son una observación de prueba, no un benchmark representativo del teléfono.

| Intervención | Total | Primer texto, incluida carga | Carga | Tokens nativos | Tokens/s nativos | PSS al terminar, antes de liberar |
|---|---:|---:|---:|---:|---:|---:|
| A · Propuesta | 46.581 ms | 40.119 ms | 27.761 ms | 48 | 4,26 | 4.209.113 KiB |
| B · Crítica | 28.530 ms | 25.951 ms | 13.545 ms | 23 | 3,05 | 4.212.046 KiB |
| A · Final | 27.044 ms | 24.468 ms | 11.535 ms | 23 | 2,91 | 4.212.262 KiB |

La respuesta final fue «La propuesta es correcta y clara. La suma de 2 más 2 es 4.». PSS es una muestra del proceso, no el pico de memoria. El cierre por falta de memoria de la prueba inicial motivó una comprobación previa conservadora; no puede impedir todas las terminaciones del sistema.

### Reproducir las pruebas Android

```sh
# Emulador x86_64; omitir -PtestAbi para un teléfono ARM64.
./gradlew -PtestAbi=x86_64 :app:assembleDebug :app:assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -r -e class org.dualmind.android.EncryptedDatabaseTest,org.dualmind.android.UiSmokeTest org.dualmind.android.test/androidx.test.runner.AndroidJUnitRunner
# Abrir la app primero para disponer de su directorio externo privado.
adb push Qwen3-1.7B_dynamic_wi4b32_afp32.litertlm /sdcard/Android/data/org.dualmind.android/files/qwen.litertlm
adb shell svc wifi disable
adb shell svc data disable
adb shell am instrument -w -r -e class org.dualmind.android.NativeModelTest -e modelPath /sdcard/Android/data/org.dualmind.android/files/qwen.litertlm org.dualmind.android.test/androidx.test.runner.AndroidJUnitRunner
```

Las pruebas no descargan pesos. Sin `modelPath`, la prueba nativa se omite explícitamente. Las capturas de la suite UI son datos sintéticos de QA y se habilitan solo durante esa prueba; la aplicación normal mantiene `FLAG_SECURE`. Después de probar, restaura Wi-Fi/datos si los necesitas.

## Pendiente de validación

- Ejecución de GitHub Actions sobre el código publicado y verificación del APK de entrega.
- Gemma3-1B-IT: el usuario decidió validarlo posteriormente en su Xiaomi, tras aceptar sus condiciones. No se descargó ni se probó.
- Dos familias distintas de modelos en un debate real: Qwen + Gemma.
- Xiaomi 15T: instalación ARM64, importación con selector SAF, aceleración GPU/fallback, cancelación durante carga nativa, carga térmica, batería, memoria bajo presión y latencias reales.
- Accesibilidad con TalkBack, tamaño de fuente máximo y versiones Android diferentes al emulador.

No se afirma soporte NPU, rendimiento del Xiaomi, cifrado de pesos ni búsqueda vectorial. La recuperación usa FTS4. La cancelación depende de la cooperación del runtime nativo; una inicialización bloqueante puede superar el plazo nominal. Los pasos físicos reproducibles están en [DEVICE_TEST_PLAN.md](DEVICE_TEST_PLAN.md).
