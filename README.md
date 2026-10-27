# Free My TV · Community fork

Personalización para Android TV / Google TV basada en [simbasjungles/free-my-tv](https://github.com/simbasjungles/free-my-tv).

Home con estante minimalista, cartelera, Jellyfin, agenda cultural de Madrid, pizarra de experiencias, controles/calendarios de Home Assistant, notificaciones MQTT y asistente Jarvis.

**[Funciones, configuración, privacidad y créditos](COMMUNITY.md)**

Esta rama contiene código y recursos genéricos. No incluye la configuración personal del mantenedor ni sus APK, credenciales o certificados de firma. Las conexiones son opcionales y cada usuario aporta sus propios datos.

- `./build.sh`: configuración genérica sin credenciales incorporadas.
- `./build.sh --personal`: compilación privada con tu propio `config.properties`; ese APK no se debe compartir.
- MQTT y la clave de ElevenLabs se configuran después de instalar, desde la TV.
- Las entidades de Home Assistant se seleccionan actualmente mediante los campos `ha_*_entity` de la configuración local. No hay aún un configurador completo de todas las integraciones desde la TV.

Las mejoras de esta rama se aplican a Android. Las implementaciones webOS/Tizen heredadas no incluyen automáticamente estas incorporaciones. No se ha publicado un APK comunitario de esta rama.

Fondos del salvapantallas: **Skyline**, de **[Basic Apple Guy](https://basicappleguy.com/)**, [colección original](https://basicappleguy.com/basicappleblog/skyline). Se conserva su atribución y los derechos de su autor.

Código derivado bajo [MIT](LICENSE). Se mantienen los avisos del proyecto original y las licencias propias de los recursos y bibliotecas de terceros.
