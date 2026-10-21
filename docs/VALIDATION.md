# Validación de la versión inicial

Comprobaciones realizadas el 20 y el 21 de octubre de 2026. Esta versión es una **alpha de desarrollo**, firmada con certificado debug. No se ha conectado un Xiaomi 15T al entorno de desarrollo.

## Comprobaciones realizadas

| Comprobación | Resultado observado |
|---|---|
| `:core:test` | 18 pruebas, 0 fallos: orquestación, rondas, cancelación, concurrencia, contexto y copias cifradas |
| `:app:testDebugUnitTest` | 5 pruebas Robolectric/SQLite, 0 fallos: persistencia, recuperación, FTS, identidades y restauración |
| `:app:assembleDebug` | APK ARM64 generado correctamente |
| `:app:assembleDebugAndroidTest` | APK de instrumentación generado correctamente |
| `:app:lintDebug` | 0 errores y 15 advertencias: versiones nuevas, ABI de ChromeOS, configuración de backup para API antiguas y recomendaciones KTX |
| Qwen3-1.7B en LiteRT-LM JVM 0.18.0 / CPU / Java 21 | Inferencia real: «Dos más dos es cuatro.» |
| SQLCipher + Android Keystore, API 35 x86_64 | Prueba instrumentada correcta: misma clave, base reabierta y contenido ausente en texto plano |
| Navegación Compose, API 35 x86_64 | Prueba instrumentada correcta de las seis pantallas y creación explícita de recuerdo |
| Debate nativo Qwen, emulador de 4 GiB | Android terminó el proceso por `LOW_MEMORY`; no cuenta como inferencia correcta |
| Debate nativo Qwen, API 35 x86_64, 6 GiB | Correcto: tres cargas secuenciales A/B/A, tres respuestas no vacías, CPU, Wi-Fi y datos desactivados |
| Revisión visual | Seis capturas reales del emulador inspeccionadas en modo claro; sin solapamientos de controles observados |
| Manifiesto fusionado debug | Sin permiso `INTERNET`; copias automáticas deshabilitadas y exclusiones explícitas de transferencia |
| APK de entrega | `apksigner verify` correcto, firma v2 debug; `zipalign -c -P 16 4` correcto; todos los segmentos ELF LOAD de las cuatro bibliotecas ARM64 alineados a 16.384 bytes |
| GitHub Actions, Ubuntu/JDK 17 | [Ejecución 39500733459](https://github.com/natsu1122/dualmind-android/actions/runs/39500733459) correcta sobre `a9ba254`: pruebas, Lint, ambos APK, firma, checksum y artefactos publicados |

## APK de entrega

- Archivo: `dualmind-0.1.0-alpha-arm64-debug.apk`, 45.760.411 bytes.
- Código compilado: `a9ba254c00676b26311032d0b725153e854c410e`. Los cambios posteriores de documentación no modifican este APK.
- Paquete `org.dualmind.android`, versión `0.1.0-alpha`, código 1, Android mínimo API 26, destino API 36, ABI `arm64-v8a`.
- SHA-256: `117f709b7c1ddf1166b1204ec527a32eb2ef3868f9d308900946a9cdb6f0b644`.
- Certificado debug SHA-256: `f602bf40eda846163b8e7a066ee30c78741f3415afb6f09888dd416398030c39`.

El APK ARM64 se compiló y verificó estáticamente; la ejecución Android se probó mediante la variante x86_64 del mismo proyecto. La instalación e inferencia ARM64 física siguen pendientes. El dispositivo y los pesos no se sustituyeron por respuestas simuladas.

El archivo de Qwen usado en las pruebas coincide con el SHA-256 publicado en `MODELS.md`. Los pesos permanecen fuera del repositorio. En Android, ambos roles usaron **el mismo archivo Qwen**, con identidades distintas. Esto verifica inferencia y orquestación real A/B/A; no certifica dos familias diferentes ni Gemma.

La primera suite nativa finalizó `OK (1 test)` en 126,643 segundos, incluyendo copia y hash del modelo. Las pruebas separadas de almacenamiento y UI finalizaron `OK (2 tests)`.

El 21 de octubre se repitió la suite conjunta sobre el código final `a9ba254`, incluida la nueva admisión de RAM y los recursos compartidos por proceso: **`OK (3 tests)` en 164,497 segundos**. Pasaron SQLCipher/Keystore, navegación y memoria explícita, y las tres generaciones reales A/B/A. Esta ejecución usó la variante x86_64, 6 GiB y redes desactivadas.

### Mediciones del emulador, no del Xiaomi

Primera ejecución correcta: contexto 2.048, máximo 64 tokens por intervención, cuatro hilos CPU, LiteRT-LM 0.18.0. El emulador tenía 6 GiB configurados. Los valores son una observación de prueba, no un benchmark representativo del teléfono.

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

- Gemma3-1B-IT: el usuario decidió validarlo posteriormente en su Xiaomi, tras aceptar sus condiciones. No se descargó ni se probó.
- Dos familias distintas de modelos en un debate real: Qwen + Gemma.
- Xiaomi 15T: instalación ARM64, importación con selector SAF, aceleración GPU/fallback, cancelación durante carga nativa, carga térmica, batería, memoria bajo presión y latencias reales.
- Accesibilidad con TalkBack, tamaño de fuente máximo y versiones Android diferentes al emulador.

No se afirma soporte NPU, rendimiento del Xiaomi, cifrado de pesos ni búsqueda vectorial. La recuperación usa FTS4. La cancelación depende de la cooperación del runtime nativo; una inicialización bloqueante puede superar el plazo nominal. Los pasos físicos reproducibles están en [DEVICE_TEST_PLAN.md](DEVICE_TEST_PLAN.md).
