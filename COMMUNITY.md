# Guía del ejemplo

Este fork conserva el ejemplo de personalización desarrollado sobre [Free My TV](https://github.com/simbasjungles/free-my-tv), commit base `ddb03c5`. Está pensado para adaptarlo directamente con un agente de IA. Las incorporaciones corresponden a Android TV / Google TV; webOS/Tizen mantienen el código heredado. La cartelera y la agenda cultural del ejemplo se centran en Madrid.

## Qué debe adaptar el agente

Busca `XXXXX` en el repositorio. Son marcadores ficticios, no credenciales ni identificadores utilizables.

| Datos | Archivo |
|---|---|
| Claves API, servidores, perfil Jellyfin, ciudad y álbum de fotos | `config.example.properties` → copia local `config.properties` |
| Sensores de temperatura y humedad | `app/src/io/freemytv/HomeClimate.java` |
| Escena, calefacción y persianas | `app/src/io/freemytv/HomeControlState.java` |
| Dispositivos de estudio y dormitorio | `app/src/io/freemytv/StudyState.java` |
| Lavadora, lavavajillas e impresora 3D | `app/src/io/freemytv/ApplianceState.java` |
| Ejemplos de alias y colores de calendarios | `app/src/io/freemytv/CalendarAgenda.java` |
| Hosts locales que necesiten HTTP | `app/res/xml/network_security_config.xml` |

Los dominios `.invalid` son ejemplos deliberadamente inexistentes. Si tus servidores utilizan HTTPS, no hace falta autorizar HTTP. Si necesitan HTTP, sustituye únicamente los hosts ficticios por los tuyos; no habilites HTTP para todos los destinos.

Se mantienen los ajustes MQTT de Android TV y la entrada de clave de ElevenLabs que ya existían en la aplicación. La clave de ElevenLabs se cifra con Android Keystore. No se añade un sistema nuevo para configurar las demás integraciones: el agente adapta el archivo local y los ejemplos del código.

Para compilar se utilizan JDK 17 y Android SDK (build-tools 34 / android-34), siguiendo la base original. `./build.sh` utiliza tu configuración local; los valores que contengan `XXXXX` se consideran sin completar. `--no-secrets` sigue disponible para comprobaciones sin cargar valores privados. Las entidades que conservan `XXXXX` no hacen consultas ni envían órdenes. No se incluye la clave de firma del mantenedor; conserva la tuya localmente para futuras actualizaciones.

## Privacidad

El repositorio no incluye la configuración privada, APK personales, certificados de firma ni respaldos del mantenedor. La captura del README se ha seleccionado expresamente para su publicación. Tu `config.properties` tampoco debe publicarse. Un APK que generes con tus credenciales puede contenerlas: no lo compartas. El historial de Git también debe revisarse antes de publicar tus propias modificaciones.

Las funciones de contenido consultan sus proveedores (TMDB, Fanart.tv, Open-Meteo, cartelera, programas y agenda cultural). Las conexiones Jellyfin/Jellyseerr y Home Assistant usan los servidores que hayas indicado; MQTT usa tu broker. El collage requiere el álbum compartido que tú elijas.

Jarvis utiliza el reconocimiento de Google para transcribir tu voz, envía el texto a tu Home Assistant y, si activas ElevenLabs, envía allí el texto de la respuesta para generar el audio. Assist puede controlar las entidades que hayas expuesto. Las credenciales MQTT permanecen en preferencias privadas; ElevenLabs utiliza almacenamiento cifrado. Las copias de seguridad Android de la aplicación están desactivadas.

## Créditos

- Proyecto original: [simbasjungles/free-my-tv](https://github.com/simbasjungles/free-my-tv), [MIT](LICENSE). Se conserva su copyright.
- Fondos **Skyline**, de **[Basic Apple Guy](https://basicappleguy.com/)**: [colección original](https://basicappleguy.com/basicappleblog/skyline). Las imágenes están recortadas a 16:9 y convertidas a JPEG; se mantienen los derechos de su autor.
- Fuentes Inter, Architects Daughter y Patrick Hand: SIL OFL 1.1, con sus avisos en `app/assets/fonts/`.
- Material Symbols y ZXing: Apache 2.0. Eclipse Paho MQTT: EDL 1.0. Licencias y procedencia en `app/libs/`, `app/assets/licenses/` y `app/assets/symbols/`.
- Datos de películas de TMDB (sin aval ni certificación de TMDB), fondos alternativos de Fanart.tv y tiempo de Open-Meteo. Se conservan también los créditos del proyecto original en [README.upstream.md](README.upstream.md).
- Los nombres y logotipos de servicios y aplicaciones pertenecen a sus respectivos titulares.
