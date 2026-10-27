# Android TV por MQTT

La TV se registra con MQTT Discovery en la integración **MQTT** de Home Assistant. Su nombre
predeterminado y el origen de Discovery son **Android TV**. Puedes elegir otro nombre para cada televisor.
No se instala una integración personalizada, HACS ni un servidor HTTP/WebSocket en la TV.

## Configuración

1. Configura MQTT en Home Assistant y utiliza ese mismo broker desde la TV. Utiliza la dirección LAN de tu broker; `broker.example.test:1883` es solo un ejemplo.
   `core-mosquitto` solo se resuelve dentro de Home Assistant. No publiques el puerto en Internet.
2. Tras actualizar la app, abre **Ajustes → Android TV**. Introduce nombre de TV, servidor (sin `mqtt://`), puerto,
   usuario y contraseña. TLS permite certificados normales de confianza del sistema; no desactiva su verificación.
3. Autoriza **Mostrar sobre otras aplicaciones** y pulsa **Guardar y conectar**. El estado debe pasar a Conectado.
   La conexión y las credenciales se guardan en los datos privados de la TV, sin recompilar una APK por casa.
4. En Home Assistant, abre **Ajustes → Dispositivos y servicios → MQTT → Dispositivos**. Aparecerá la TV con diez
   entidades: conexión, pantalla, inicio, salvapantallas, permiso de avisos, avisos pendientes, aviso activo,
   notificación normal, notificación crítica y eventos de avisos. Puedes renombrar el dispositivo desde allí.
5. Cada TV conserva un UUID independiente del nombre o la IP. Repite la configuración para añadir más televisores.
   Actualizar conserva ese UUID; borrar los datos o desinstalar crea una identidad nueva.

La lectura de sensores, los controles y los calendarios utilizan la conexión REST de Home Assistant adaptada
en el ejemplo. No dependen del antiguo componente personalizado.

## Envío sencillo

Las entidades **Notificación normal** y **Notificación crítica** funcionan con la acción estándar `notify.send_message`:

```yaml
action: notify.send_message
target:
  entity_id: notify.android_tv_notificacion_normal
data:
  message: La lavadora ha terminado
```

Con el nombre predeterminado se proponen `notify.android_tv_notificacion_normal` y
`notify.android_tv_notificacion_critica`. Comprueba los identificadores que Home Assistant haya asignado.
Los avisos críticos son rojos y persistentes: reciben el foco en la tarjeta completa y se cierran
pulsando OK, sin botón adicional.

## Avisos completos con selector de dispositivo

Opcionalmente copia [la plantilla](blueprints/script/android_tv_notify.yaml) a
`/config/blueprints/script/android_tv/notify.yaml`. Recarga las plantillas y crea un script a partir de
**Android TV · Notificación**. Elige el dispositivo una vez y asigna un nombre al script, por ejemplo
`script.aviso_tv_salon`. No requiere reiniciar Home Assistant para cargar un componente Python.

La acción del script permite mensaje, tipo (`normal`/`critical`), persistencia, duración (2–120 s) e identificador
opcional. Una misma ID actualiza el aviso. Ejemplo:

```yaml
action: script.aviso_tv_salon
data:
  message: Hay una fuga de agua en la cocina
  notification_type: critical
  persistent: true
  duration: 8
  notification_id: fuga_cocina
response_variable: entrega
```

Un aviso persistente se mantiene hasta pulsarlo; el apagado también lo elimina. Los normales persistentes conservan
el botón Entendido; los críticos reciben foco directamente en la tarjeta y siempre son
persistentes aunque se envíe persistent=false. La duración solo se utiliza para avisos normales no persistentes.
El script devuelve `published` e ID al publicar, o `discarded` si el dispositivo está desconectado/apagado.
**Publicado no significa mostrado**: se confirma por el evento `shown`. Si se pierde la conexión antes de recibir
un evento, la entrega queda desconocida; no se reintenta automáticamente.

## Protocolo público

Prefijo de cada TV: `android_tv/<UUID>` (el UUID aparece en Ajustes → Android TV).

| Topic | Contenido | Retain |
| --- | --- | --- |
| `availability` | `online` / `offline`, con último testamento | Sí |
| `awake` | Pantalla encendida: `online` / `offline` | Sí |
| `state` | JSON de estados de la TV | Sí |
| `notify/normal` | Mensaje literal, ocho segundos | **No** |
| `notify/critical` | Mensaje literal crítico persistente | **No** |
| `notify/set` | JSON con opciones completas | **No** |
| `event` | Confirmaciones JSON | **No** |

Comando completo (`qos: 0`, `retain: false`):

```json
{"message":"Puerta abierta","notification_type":"critical","persistent":true,"duration":8,"notification_id":"puerta"}
```

ID, duración y persistencia tienen valores por defecto. `expires_at` opcional es una fecha límite de recepción
Unix en milisegundos (hasta 30 segundos en el futuro); la plantilla fija diez segundos. No limita la duración de
visualización de un aviso persistente. El mensaje admite hasta 500 caracteres Java y el comando hasta 4096 bytes.
Eventos: `shown`, `queued`, `acknowledged`, `expired`, `discarded`, `rejected`; incluyen `notification_id` cuando el
comando es válido y `reason`. La entidad MQTT Event expone esos datos para automatizaciones.

La sesión MQTT es limpia y sin almacenamiento de avisos. Las suscripciones usan QoS 0. Los comandos recibidos con
la pantalla apagada se descartan, igual que todos los activos/en espera al apagar. No llegan tras despertar. Los
replays retenidos se ignoran al suscribirse: **no publiques avisos con retain activado**, aunque el broker permita
hacerlo. Discovery y estados sí se conservan para reconstruir el dispositivo al reconectar o reiniciar HA.

Si utilizas ACL, el usuario de la TV necesita leer su `notify/+` y `homeassistant/status`, y publicar su prefijo y
`homeassistant/{binary_sensor,sensor,notify,event}/android_<UUID>/+/config`. Limita los accesos a cada TV si procede.

## Validación del código

Eclipse Paho Java 1.2.5 está incluido y fijado por SHA256 en `app/libs` (licencia EDL en assets). No requiere Gradle.
El empaquetado también incluye nls/*.properties y META-INF/services de Paho, necesarios en Android; dos tests
comprueban la APK real y que las fuentes/rutas se conservan. Las pruebas usan un broker AMQTT temporal en localhost y el transporte Java real: dos TVs, retención, sesiones
limpias, eventos, último testamento y reconexión tras reiniciar el broker. Los tests de política cubren prioridad,
persistencia, cola y descarte al apagar.
