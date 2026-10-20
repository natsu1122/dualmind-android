# Fuentes y selección técnica

Consulta realizada el 20 de octubre de 2026:

- API Kotlin: https://github.com/google-ai-edge/LiteRT-LM/blob/v0.18.0/docs/api/kotlin/getting_started.md
- Código API fijado: https://github.com/google-ai-edge/LiteRT-LM/tree/v0.18.0/kotlin/java/com/google/ai/edge/litertlm
- Versiones Maven LiteRT-LM: https://dl.google.com/dl/android/maven2/com/google/ai/edge/litertlm/litertlm-android/maven-metadata.xml
- Referencia Android: https://github.com/google-ai-edge/gallery
- Ejemplos oficiales: https://github.com/google-ai-edge/litert-samples
- Compose Compiler: https://developer.android.com/jetpack/compose/compiler
- Versiones AndroidX: https://developer.android.com/jetpack/androidx/versions
- AGP: https://developer.android.com/build/releases/gradle-plugin
- SQLCipher Android: https://github.com/sqlcipher/sqlcipher-android
- Keystore: https://developer.android.com/privacy-and-security/keystore
- Modelos: enlaces y archivos en `MODELS.md`.

Maven confirmó LiteRT-LM 0.18.0 como release publicado. El catálogo Qwen menciona metadatos actualizados para ese runtime. Se consultaron `Engine.kt`, `Conversation.kt`, `Config.kt` y `Benchmark.kt` de la etiqueta 0.18.0 para verificar carga, cierre, cancelación, máximos de salida y mediciones.

Se revisaron versiones publicadas de AGP, Compose, Room, DataStore y SQLCipher. Se fija AGP 8.13.2 con Gradle 8.13; LiteRT-LM 0.18.0 requiere metadatos Kotlin 2.4.0, Kotlin Reflect 2.4.0 y Coroutines 1.11.0, comprobados en su POM y compilación. Por ello se fija Kotlin 2.4.0, KSP 2.3.12 y se actualiza explícitamente D8/R8 a la versión publicada 9.5.28 mediante el classpath de build, según la tabla https://developer.android.com/build/kotlin-support. No se desactiva la validación de metadatos Kotlin. SQLCipher 4.19.1 corresponde al release publicado consultado. Las mediciones LiteRT-LM requieren `@OptIn(ExperimentalApi::class)` y son opcionales.

Gradle 8.13 sigue siendo aceptado por Kotlin 2.4.0, aunque este anuncia que Kotlin 2.5 exigirá al menos 8.14.4. No se suprime ese aviso. El runtime JVM de LiteRT-LM tiene clases Java 21; esa condición de la prueba de escritorio es independiente del JDK 17 usado para construir Android.

No se copió Google AI Edge Gallery ni se adaptó su interfaz. El código de la aplicación es original y usa las bibliotecas mediante sus APIs públicas. No se distribuyen ejemplos descargados para investigación.
