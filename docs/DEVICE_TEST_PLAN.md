# Validación en Xiaomi 15T

Registrar versión de Android/HyperOS, APK/commit, hash de modelos, contexto, tokens, backend, temperatura ambiente y carga de batería. Los tiempos del PC o emulador no son resultados del Xiaomi.

1. Instalar APK debug ARM64 y abrir. Recorrer las seis pantallas con modo claro/oscuro, fuente grande y TalkBack; rotar el teléfono.
2. Importar Qwen INT4 del catálogo. Probar archivo truncado, extensión incorrecta, hash erróneo, cancelación y espacio insuficiente. Verificar que no quedan importaciones parciales.
3. Ejecutar Probar con CPU. Confirmar salida de texto real y métricas. Probar GPU por separado; registrar éxito o error/fallback.
4. Aceptar licencia Gemma en su sitio, importar el archivo INT4 genérico y ejecutar Probar. Asignar A/B.
5. Rápido: comprobar que solo aparece A. Debate: comprobar A/B/A, nombres de archivo y salida diferente por rol. Profundo: configurar dos y luego cuatro rondas y contar intervenciones.
6. Cancelar durante generación, volver a generar y verificar ausencia de ejecuciones simultáneas. Cancelar durante carga y registrar demora. Probar tiempo máximo y límites pequeños.
7. Guardar un recuerdo global y uno de cada uno de dos proyectos con una palabra distintiva. Consultar desde un proyecto y comprobar los recuerdos incluidos en cada intervención. Editar y borrar; comprobar búsquedas futuras.
8. Cambiar todas las propiedades de identidad de A/B. Sustituir A por otro modelo y comprobar que recuerdos e identidad permanecen.
9. Cerrar la app, forzar detención y reiniciar el teléfono. Verificar persistencia y que un debate cortado aparece Interrumpido, nunca Completo.
10. Desactivar guardado automático, crear chat temporal, terminar el proceso y verificar que no está en la base al reabrir.
11. Exportar copia con contraseña, importar, probar contraseña incorrecta, archivo manipulado y copia de versión no compatible. Confirmar que un fallo no modifica los datos existentes.
12. Probar limpieza de historial y eliminación total. Asegurarse de que las copias externas no se borran por accidente.
13. En modo avión y sin Wi-Fi, repetir Rápido y Debate. Revisar que la app no solicita permisos de red o acceso amplio a archivos.
14. Ejecutar `./gradlew :app:connectedDebugAndroidTest` con USB autorizado. Esto verifica SQLCipher/Keystore, no los modelos.
15. Sesión de 20 minutos: registrar PSS, tiempos, tokens/s reales, temperatura y batería. Comprobar comportamiento al pasar a segundo plano y ante presión de memoria. No prometer continuidad si Android termina el proceso.

Una prueba fallida se registra con el paso, configuración y error sin conversaciones personales. No compartir bases ni capturas con datos sensibles.
