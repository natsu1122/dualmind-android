# Instrucciones para agentes

- Lee README y docs/VALIDATION.md antes de cambiar el proyecto.
- No declares pruebas, compatibilidad de modelo o resultados de dispositivo sin evidencia.
- El motor de producción debe seguir usando inferencia local real. Los fakes solo están en tests.
- No incluyas permiso INTERNET, telemetría ni servicios remotos sin autorización expresa.
- No descargues ni redistribuyas pesos con condiciones de acceso sin la aceptación del usuario.
- Mantén un único motor nativo activo. Cierra conversaciones y motores tras finalizar callbacks.
- No destruyas la base para resolver una migración. Conserva identidades y memoria al cambiar modelos.
- No publiques secretos, keystores, datos personales ni directorios build.
- Usa `./gradlew :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`.
- Registra las validaciones físicas pendientes y no marques la alpha como producción.
- Las copias deben ser autenticadas, limitadas en tamaño y restauradas transaccionalmente.
