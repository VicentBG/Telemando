# Publicar una versión

La app de producción usa **una clave privada de firma distinta de la de debug**. La clave, contraseñas y APK compiladas nunca se suben a Git. Conserva la clave y la contraseña en un lugar seguro: sin ellas no podrás publicar actualizaciones instalables sobre una versión anterior.

1. Incrementa `versionCode` y `versionName` en `app/build.gradle.kts`.
2. Configura cuatro variables de entorno **fuera del repositorio**: `TELEMANDO_RELEASE_KEYSTORE` (ruta absoluta al PKCS12), `TELEMANDO_RELEASE_PASSWORD`, `TELEMANDO_RELEASE_ALIAS` y `TELEMANDO_RELEASE_KEY_PASSWORD`. No las pases como parámetros de Gradle, no las imprimas en logs y no incluyas el almacén ni los secretos en ningún commit. `assembleRelease` falla expresamente si no se configuran.
3. Compila y prueba con JDK 17 y SDK Android 36:

   ```sh
   ./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleRelease
   apksigner verify --verbose --print-certs app/build/outputs/apk/release/app-release.apk
   sha256sum app/build/outputs/apk/release/app-release.apk
   ```

4. Comprueba que el certificado de firma conserva la misma huella que la versión pública anterior. Anota la nueva huella SHA-256 de la APK en las notas de lanzamiento; nunca publiques la clave privada.
5. Prueba instalación y navegación en un dispositivo real. Etiqueta el commit ya probado como `vX.Y.Z` y publica el APK firmado en GitHub Releases.

La APK `debug` y la `release` tienen firmas distintas: para pasar de debug a la primera release es necesario desinstalar debug y volver a emparejar la TV. Las futuras releases firmadas con la **misma** clave sí se actualizarán sobre la release anterior.
