# Contribuir

Usa JDK 17 y el wrapper. Ejecuta pruebas, lint y compilación antes de proponer cambios. Mantén las dependencias fijadas; justifica actualizaciones del runtime con pruebas sobre archivos de modelos concretos.

No incluyas modelos, bases, conversaciones personales, credenciales, claves de firma ni archivos de copia. Usa datos sintéticos en tests.

Separa el dominio en `core`, la persistencia en `data` y el adaptador nativo en `inference`. Todo motor falso pertenece a tests. No añadas fallback remoto ni respuestas de demostración a producción.

Cualquier cambio de esquema Room debe aumentar la versión, aportar migración no destructiva y probar reapertura de datos de la versión anterior. No borres datos para arreglar errores de migración.

Documenta las pruebas de dispositivo con modelo, hash, Android, backend y configuración. No uses resultados de emulador como evidencia de rendimiento del Xiaomi. Las contribuciones de código original se entregan bajo Apache-2.0.
