# Modelos y compatibilidad

Verificación de catálogo: 20 de octubre de 2026. Runtime fijado: LiteRT-LM Android 0.18.0. No se descargan modelos desde la aplicación.

| Agente previsto | Archivo exacto | Tamaño del catálogo | Licencia |
|---|---|---:|---|
| A | `Qwen3-1.7B_dynamic_wi4b32_afp32.litertlm` | 977.184.032 bytes | Apache-2.0 |
| B | `Gemma3-1B-IT_multi-prefill-seq_q4_ekv4096.litertlm` | 584.417.280 bytes | Gemma Terms |

Origen de Qwen: https://huggingface.co/litert-community/Qwen3-1.7B/tree/main

SHA-256 público de la variante elegida de Qwen:

```text
2a72330b504a6596d53a2c00923c57173b10f1b83e6a541213ffec08d1fc5de8
```

Este hash corresponde al archivo del catálogo consultado y cambia si el proveedor modifica el contenedor, incluso sin cambiar los pesos. Una discrepancia impide importarlo bajo ese nombre; comprueba la revisión oficial antes de actualizar el catálogo. No desactives la verificación para ocultar una discrepancia.

Origen de Gemma: https://huggingface.co/litert-community/Gemma3-1B-IT/tree/main

Gemma exige aceptar sus condiciones en Hugging Face. No se incluyen pesos, credenciales ni mecanismos para saltar el acceso. La API pública oculta su hash LFS sin autorización. El usuario puede introducir un SHA-256 obtenido de una fuente confiable; en caso contrario se calcula una huella local y la UI indica que no autentica el origen. `gemma3-1b-it-int4.litertlm` aparece en el catálogo con el mismo tamaño y objeto, pero se recomienda el nombre descriptivo anterior.

Qwen publica además `Qwen3_1.7B.litertlm`, de 2.056.729.520 bytes. La variante dinámica INT4 se elige por su menor tamaño. No se seleccionan archivos `.task`, `.gguf` ni variantes NPU específicas de otros SoC.

## Compatibilidad comprobable

La documentación de LiteRT-LM usa Gemma3-1B-IT y el catálogo Qwen actualiza metadatos para 0.18.0. Esto fundamenta la integración, pero **no demuestra ejecución en Xiaomi 15T**. Cada modelo tiene un botón Probar que hace generación real. Los fallos no se reemplazan por texto ficticio. La aplicación acepta otros `.litertlm` y mantiene el motor detrás de `InferenceEngine`.

Qwen se ejecutó realmente con CPU en escritorio y en Android API 35 x86_64, incluido un debate A/B/A con el mismo archivo. El emulador de 4 GiB fue insuficiente; con 6 GiB la prueba pasó. Se observó PSS de unos 4,0 GiB al terminar cada intervención. Gemma y la combinación Qwen + Gemma quedan pendientes de prueba física. Los detalles están en [VALIDATION.md](VALIDATION.md).

Para archivos externos se aplica un techo conservador de 4.096 tokens. La aplicación no interpreta automáticamente todos los límites internos de un contenedor. Si un modelo requiere un contexto inferior, redúcelo en Ajustes; el runtime puede rechazar formatos o parámetros incompatibles. Modelos que solo admitan NPU no están soportados por esta versión.

La importación comprueba extensión, tamaño mínimo, límite de 12 GiB, espacio libre y SHA-256 cuando hay referencia. La compatibilidad estructural y de runtime se comprueba al cargar/generar. El tamaño del archivo se vuelve a comprobar antes de inferir. No se afirma que comprobar una extensión o calcular una huella valide por sí solo un modelo.

## RAM, almacenamiento y rendimiento

Los dos archivos recomendados suman unos 1,56 GB decimales en almacenamiento privado. El selector copia los originales, de modo que durante la importación se necesita espacio adicional. La RAM depende del contexto, los tensores y el backend; no se deduce del tamaño del archivo. Se comprueba presión de memoria Android y se capturan errores recuperables de memoria. Una terminación del proceso por Android o un fallo nativo no es recuperable dentro de ese proceso; el diario se recupera al abrir de nuevo.

CPU usa cuatro hilos. GPU se puede solicitar y la carga fallida reintenta en CPU. Un modelo que no admita CPU seguirá fallando: no existe garantía universal de fallback. La inicialización fallida del fabricante debe liberar sus recursos nativos; la aplicación cierra todos los handles que el runtime devuelve correctamente.
