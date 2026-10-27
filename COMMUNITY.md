# Versión comunitaria de Free My TV

Este fork parte de [simbasjungles/free-my-tv](https://github.com/simbasjungles/free-my-tv), bajo licencia MIT. Las personalizaciones Android se desarrollaron sobre el commit `ddb03c5`. El proyecto original continúa evolucionando; esta rama no incorpora automáticamente sus cambios posteriores.

## Qué incluye

- Home con fondos de cine, botones de tráiler, sesiones y solicitudes a Jellyseerr, estante minimalista e indicadores compactos.
- Contenido reciente y en reproducción de Jellyfin, y los últimos episodios de programas de Atresplayer y RTVE.
- Agenda cultural de Madrid con filtros temporales, categorías, preferencias y códigos QR.
- Pizarra de experiencias editable, navegación con el mando y collage opcional de un álbum compartido.
- Salvapantallas con fondos según el clima y la hora, reloj, fecha, información de casa y calendario.
- Controles de Home Assistant, indicadores de electrodomésticos y calendario navegable.
- HAndroid TV mediante MQTT Discovery, notificaciones y asistente de voz Jarvis con salida Android o ElevenLabs.

Las incorporaciones de esta rama se centran en Android TV / Google TV. No se afirma que estas nuevas funciones estén implementadas en las versiones webOS/Tizen heredadas del original. La cartelera y la agenda cultural siguen siendo específicas de Madrid y del cine implementado; no son buscadores universales por ciudad.

## Configuración y compilación

Se necesitan JDK 17 y Android SDK con build-tools 34 y android-34, como en el proyecto original. No se distribuye una clave de firma ni un APK privado del mantenedor.

`./build.sh` genera una configuración genérica: no incorpora los valores privados de `config.properties`. Las funciones que necesitan un servidor o una clave permanecen sin conexión hasta configurarlas. No hay un asistente completo de configuración desde la TV para todos los servicios en esta rama.

Para adaptar las conexiones y entidades a tu instalación:

1. Copia `config.example.properties` a `config.properties`, que está excluido de Git.
2. Completa únicamente las integraciones que quieras utilizar. Los campos `ha_*_entity` vinculan cada control/sensor a tus entidades; vacíos no consultan ni controlan dispositivos.
3. Usa HTTPS para tus servidores. Si necesitas HTTP dentro de tu red, indica explícitamente sus hosts en `local_http_hosts`, separados por comas y sin protocolo ni puerto. No se habilita HTTP globalmente.
4. Ejecuta `./build.sh --personal`. **Ese APK contiene las credenciales de los servicios configurados y debe permanecer privado.** No lo adjuntes a issues, releases ni mensajes públicos.
5. Mantén localmente el archivo de firma creado durante la compilación para poder actualizar tu propia instalación.

MQTT se configura en la TV, en HAndroid TV. La clave de ElevenLabs se introduce en los ajustes de Jarvis y se cifra mediante Android Keystore; no se incorpora al APK. Cada usuario aporta sus propias cuentas, permisos y claves, y debe comprobar las condiciones y cuotas de los proveedores.

No se incluye ningún servidor, usuario, álbum, calendario o entidad de la instalación del mantenedor. Los identificadores del archivo de ejemplo son ficticios, y las entidades quedan vacías por defecto. La pizarra nueva empieza vacía. Los nombres de calendario proceden de la propia instalación de Home Assistant.

## Privacidad

No subas `config.properties`, archivos de firma, APK personales, capturas de tu casa, volcados de interfaz o registros de depuración. Revisa también los commits y el contenido de cualquier archivo que adjuntes: `.gitignore` no elimina secretos que ya se hayan comprometido.

Las integraciones son opcionales. La aplicación puede contactar con TMDB, Fanart.tv, Open-Meteo y los proveedores de contenido, cartelera y eventos utilizados por cada bloque. Una vez configurada, consulta tu servidor Jellyfin/Jellyseerr y Home Assistant; las notificaciones usan tu broker MQTT. Las fotografías del collage se obtienen del álbum compartido configurado por el usuario; no se implementa acceso a álbumes privados mediante OAuth.

En Jarvis, el servicio de reconocimiento de Google transcribe el audio; el texto se envía a tu Home Assistant. Si activas ElevenLabs, el texto de la respuesta se envía a ElevenLabs para generar la voz. No es una ruta de voz íntegramente local. Assist puede actuar sobre las entidades que hayas expuesto en Home Assistant. Los permisos de voz y el cambio de asistente son opcionales y explícitos.

Las credenciales MQTT se guardan en preferencias privadas de la aplicación. La clave de ElevenLabs se cifra con Android Keystore. Las demás credenciales configuradas durante una compilación personal son recuperables del APK: por eso esos APK no se deben compartir. Las copias de seguridad de datos de Android están desactivadas para la aplicación.

## Créditos y recursos

- Proyecto original y licencia: [Free My TV](https://github.com/simbasjungles/free-my-tv), [MIT](LICENSE). Se conserva su aviso de copyright.
- Fondos **Skyline**, creados por **[Basic Apple Guy](https://basicappleguy.com/)**: [colección original y descargas](https://basicappleguy.com/basicappleblog/skyline). Las imágenes incluidas son versiones recortadas a 16:9 y convertidas a JPEG. La atribución no convierte estos recursos en código bajo MIT; se mantienen los derechos de su autor.
- Fuentes Inter, Architects Daughter y Patrick Hand: SIL Open Font License 1.1; avisos y licencias en `app/assets/fonts/`.
- Material Symbols: Apache 2.0; licencia en `app/assets/symbols/`. Logotipos de las aplicaciones y Home Assistant pertenecen a sus respectivos titulares.
- Eclipse Paho MQTT: distribuido bajo EDL 1.0. ZXing: Apache 2.0. Versiones, origen, hashes y licencias en `app/libs/` y `app/assets/licenses/`.
- Datos de películas: este producto utiliza la API de TMDB, pero no está avalado ni certificado por TMDB. Fanart.tv aporta fondos alternativos cuando está configurado. El tiempo procede de Open-Meteo; se conservan los créditos adicionales del proyecto original.

Consulta [README.upstream.md](README.upstream.md) para la documentación histórica del proyecto base. Las instrucciones de esta página prevalecen para la configuración y el tratamiento de datos de esta rama.
