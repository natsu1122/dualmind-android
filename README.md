# DUAL MIND

Aplicación Android original para deliberación entre dos modelos locales, memoria persistente y control de los datos. Interfaz en español, Kotlin, Compose Material 3, Room y LiteRT-LM.

**Versión inicial de desarrollo.** Qwen generó un debate real A/B/A en Android emulado, sin red y con cargas secuenciales. Gemma y el Xiaomi 15T quedan pendientes de validación física. El estado completo está en [VALIDATION.md](docs/VALIDATION.md). No se incluyen pesos ni respuestas simuladas.

## Instalación

1. Descarga el APK identificado como **development/debug** desde [Releases](https://github.com/natsu1122/dualmind-android/releases), o desde el artefacto `dualmind-development-apk` de una ejecución correcta de [Android CI](https://github.com/natsu1122/dualmind-android/actions).
2. Necesitas Android 8.0/API 26 o posterior y procesador ARM64. La referencia prevista es Xiaomi 15T, Dimensity 8400-Ultra, 12 GB RAM. Se recomienda Android actualizado.
3. Abre el APK en el teléfono y autoriza la instalación desde esa aplicación cuando Android lo solicite. Es una compilación de pruebas; no está publicada en Play Store.
4. Descarga los modelos en el navegador siguiendo [MODELS.md](docs/MODELS.md). Para Gemma, inicia sesión y acepta personalmente sus condiciones.
5. En **Modelos**, elige el origen, importa el `.litertlm` y ejecuta **Probar**. Asigna Qwen a A y Gemma a B. La importación copia los archivos al almacenamiento privado: necesitas espacio para el original y la copia. Puedes eliminar el original después.
6. En **Chat**, selecciona Rápido para una primera prueba con A. Después prueba Debate, abre la pestaña Debate y comprueba las intervenciones A/B/A.
7. Activa modo avión y repite la prueba. La aplicación no declara el permiso `INTERNET`; la inferencia no usa servicios remotos.

Por USB:

```sh
adb install -r app-debug.apk
adb shell am start -n org.dualmind.android/.MainActivity
```

Si Android informa de una firma distinta, no desinstales sin exportar una copia cifrada: desinstalar elimina los datos y los modelos privados. Las compilaciones debug de diferentes máquinas/CI pueden tener certificados distintos.

## Compilar de forma reproducible

Instala JDK 17, Android SDK plataforma 36 y Build Tools 35.0.0. Configura `ANDROID_HOME` o crea `local.properties` con `sdk.dir=/ruta/al/sdk`. Abre la carpeta raíz en Android Studio compatible con AGP 8.13.2.

```sh
./gradlew :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

En Windows usa `gradlew.bat`. El resultado está en `app/build/outputs/apk/debug/app-debug.apk`. El wrapper fija Gradle 8.13 y verifica el SHA-256 de su distribución. La primera compilación necesita Internet para obtener herramientas y dependencias; el APK no lo necesita para inferencia.

Versiones fijadas: AGP 8.13.2, Kotlin 2.4.0, KSP 2.3.12, R8 9.5.28, Compose BOM 2025.10.01, Room 2.8.5, DataStore 1.2.1, SQLCipher 4.19.1, LiteRT-LM 0.18.0 y Coroutines 1.11.0. Se eligió una combinación estable del entorno Gradle 8, no versiones dinámicas ni previews. Véase [RESEARCH.md](docs/RESEARCH.md).

## Cómo funciona

- **Rápido:** una intervención de A.
- **Debate:** propuesta de A, crítica de B y síntesis de A.
- **Profundo:** propuesta y entre una y cuatro rondas de crítica/revisión. Hay límites de salida, contexto, caracteres y tiempo.
- Cada intervención crea, utiliza y cierra un motor y una conversación. No se mantienen dos modelos en RAM.
- CPU es el valor inicial. GPU es opcional, con reintento en CPU si falla la inicialización. No hay integración NPU ni afirmación de soporte NPU para MediaTek.
- La cancelación solicita detener el procesamiento nativo y espera su finalización antes de liberar recursos. Una carga nativa bloqueante puede demorar la cancelación y exceder el plazo nominal.
- El debate guarda intervenciones completas y puntos de control parciales. Tras cerrar el proceso, los debates en curso se marcan **Interrumpido**. No se reanudan automáticamente.

## Memoria y privacidad

Las conversaciones, recuerdos, proyectos e identidades se almacenan en Room sobre SQLCipher. Android Keystore protege la clave que cifra la clave de la base. No hay telemetría, cuentas ni servidor obligatorio. El respaldo automático del sistema está deshabilitado. Los modelos están en almacenamiento interno privado y no se cifran por la aplicación, pues el runtime necesita acceso al archivo.

Los recuerdos personales solo se guardan por acción explícita. Puedes editarlos o borrarlos en **Memoria**, asociarlos a proyectos y clasificarlos como decisión, objetivo o pendiente. El historial puede desactivarse en Ajustes: las nuevas conversaciones quedan en memoria de proceso y no persisten. El historial anterior permanece hasta que lo elimines.

La recuperación es **búsqueda textual FTS4**, con coincidencias de términos y refuerzo de recuerdos del proyecto. No son embeddings ni búsqueda semántica vectorial. Se seleccionan como máximo ocho recuerdos dentro de un presupuesto conservador en bytes UTF-8; cada intervención muestra los recuerdos realmente incluidos. Se añade historial reciente del chat, no toda la base. Los datos recuperados se delimitan como contexto, pero ningún modelo garantiza inmunidad frente a instrucciones maliciosas dentro del texto.

Eliminar un recuerdo evita su uso futuro; las copias del contexto de debates anteriores permanecen en el historial hasta limpiarlo. Las copias exportadas requieren eliminación aparte.

Las copias `.dmbackup` usan AES-256-GCM y PBKDF2-HMAC-SHA256, salt aleatorio y 210.000 iteraciones. Se exige una contraseña de 12 caracteres como mínimo. La importación valida tamaño, formato y relaciones antes de reemplazar los datos en una transacción. No se exportan claves, modelos ni asignaciones; las importaciones mantienen los modelos instalados. Límite de contenido de copia: 20 MiB. **Sin contraseña no hay recuperación.**

## Estructura

```text
core/                 Dominio Kotlin/JVM, orquestador, presupuesto de contexto y cifrado de copias
app/
  src/main/.../data/  Room, FTS, claves, preferencias, importación de modelos y copias
  src/main/.../inference/  Adaptador real LiteRT-LM
  src/main/.../ui/    ViewModel y seis pantallas Compose
  src/test/          Pruebas Room/SQLite con Robolectric
  src/androidTest/   SQLCipher/Keystore, navegación UI y debate nativo opcional con pesos externos
  schemas/           Esquema Room versionado
docs/                 Fuentes, arquitectura, modelos, validación y firma
licenses/             Licencias y avisos de terceros incluidos también en el APK
.github/workflows/    Compilación, pruebas, lint y APK como artefacto
```

## Pruebas

```sh
./gradlew :core:test :app:testDebugUnitTest :app:lintDebug
./gradlew :app:assembleDebugAndroidTest
# Con un dispositivo ARM64 conectado y autorizado:
./gradlew :app:connectedDebugAndroidTest
```

Los dobles de inferencia están exclusivamente en `core/src/test`. Las pruebas JVM verifican orquestación, cancelación y almacenamiento; no ejecutan el runtime Android ni sustituyen su validación. El procedimiento físico está en [DEVICE_TEST_PLAN.md](docs/DEVICE_TEST_PLAN.md). No hay benchmarks del Xiaomi obtenidos en emulador.

## Distribución

La versión debug es para pruebas. Para distribuir, usa una clave privada propia fuera del repositorio y sigue [SIGNING.md](docs/SIGNING.md). CI nunca sube claves. Mantén la misma firma para actualizar sin perder datos. Las actualizaciones de esquema deben añadir migraciones; no se usa `fallbackToDestructiveMigration`.

## Licencias

Código original: **Apache License 2.0**, SPDX `Apache-2.0`. [LICENSE](LICENSE) contiene el texto completo. Esta licencia no modifica licencias de bibliotecas, modelos ni los términos de Gemma. Consulta [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md), [NOTICE](NOTICE) y [PRIVACY.md](PRIVACY.md).
