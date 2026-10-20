# Firma y distribución

El APK debug usa una clave de desarrollo local generada por Android. No es una versión de producción. Diferentes máquinas y ejecuciones de CI pueden firmar con claves distintas; la actualización exige el mismo certificado.

Para una distribución mantenible:

1. Genera una clave de firma propia mediante **Build → Generate Signed App Bundle or APK** en Android Studio.
2. Guarda el keystore fuera del checkout y realiza una copia protegida. No lo envíes a un chat, issue o repositorio.
3. Crea un APK release firmado con esa clave. Esta versión no contiene configuración de firma privada.
4. Ejecuta pruebas de dispositivo y verifica firma/alineación con las herramientas del SDK:

```sh
apksigner verify --verbose --print-certs dualmind-release.apk
zipalign -c -P 16 4 dualmind-release.apk
```

5. Publica solo después de validar los modelos y la persistencia. Mantén `applicationId`, firma y migraciones compatibles para conservar los datos.

Para firmar en CI en el futuro, usa secretos de GitHub y un entorno protegido; nunca valores literales en YAML. Esta entrega no configura secretos ni publica una release de producción automáticamente.
