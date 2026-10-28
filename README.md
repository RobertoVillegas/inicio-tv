# TV TCL de la sala: declutter e inicio propio

Documentación de los cambios hechos a la Smart TV **TCL "Smart TV Pro"** de la sala, a partir del **28 de octubre de 2026**.
Si te encontraste esta carpeta y no sabes qué le pasó a la TV, empieza aquí.

---

## TL;DR

- Se desactivaron **22 apps** que no se usan: streaming sin uso, Asistente de voz y anuncios de TCL.
- Se instaló **SmartTube** para que los invitados vean YouTube sin la cuenta del dueño.
- Se hizo una app de inicio propia, **Inicio**. Muestra paisajes de Wikimedia Commons que se mueven despacio, la hora, el clima, el lugar de cada foto y solo las apps que se usan.
- Inicio también funciona como **protector de pantalla** y se configuraron tiempos de apagado para cuidar el panel.

**No se borró nada.** Todo es reversible, y en `respaldo-original/` está el estado de fábrica.

Para revertirlo todo: `./restaurar-todo.sh`.
Si la TV se queda sin pantalla de inicio: `./rescate-launcher.sh`.

---

## El equipo

| | |
|---|---|
| Modelo | TCL "Smart TV Pro" (device `G10`), 55", panel 3840×2160 |
| Nombre en la red | `TV de Sala` |
| IP | `192.168.0.122` por DHCP, así que puede cambiar (ver abajo) |
| SO | Google TV, Android 12, build `STT2.230203.001` |
| Parche de seguridad | 2025-12-05 |
| SoC | Realtek RTD2875P (`rtd6748`) |
| RAM | 1.8 GB |
| Almacenamiento | 7.9 GB en `/data` |
| Arquitectura | **`armeabi-v7a` solamente (32 bits)**: al bajar APKs, usa la variante `armeabi-v7a` |
| Interfaz | Se dibuja a **1920×1080**. Solo el video sale en 4K |

---

## Cómo conectarse

```bash
brew install --cask android-platform-tools   # si no tienes adb
adb connect 192.168.0.122:5555
adb devices -l                                # debe decir "device", no "unauthorized"
```

**Si la IP cambió**, la TV se anuncia sola por mDNS:

```bash
adb mdns services        # busca adb-D00650B30610C507F  _adb._tcp  <IP>:5555
```

Todos los scripts aceptan otra IP con `TV=<ip>:5555 ./script.sh`.

**Si dice `unauthorized`**, la TV está mostrando un diálogo que pide autorizar esta computadora. Acéptalo con el control y marca *"Permitir siempre desde esta computadora"*.

**Si la depuración está apagada**: en la TV ve a Ajustes → Sistema → Acerca de y presiona **OK 7 veces** sobre *Compilación del SO de Google TV*. Luego ve a Ajustes → Sistema → **Opciones para desarrolladores** y activa **Depuración por USB**.

**Si en macOS `adb` responde "No route to host"**, el problema es *Local Network Privacy* y no la TV. Ve a Ajustes del Sistema → Privacidad y seguridad → **Red local** y dale permiso a tu terminal.

---

## Qué se cambió

### 1. Declutter: 22 apps desactivadas (`fase1-desactivar.txt`)

Método: `pm disable-user --user 0 <paquete>`. Una app desactivada sigue apareciendo en Ajustes → Apps y se puede reactivar desde ahí con el control, sin computadora.

- **Apps instaladas sin uso:** Disney+, Max, Paramount+, Tubi, Twitch, PrendeTV, Downloader y `com.flix.tv`.
- **Google:** Asistente y búsqueda por voz (`katniss`), YouTube Music, Play Juegos, Feedback y el protector de pantalla de Google (`dreamx`), que mostraba contenido promocionado.
- **TCL:** canales con anuncios (`channelplus`), modo tienda (`esticker`, `exhibit`), Alexa, MagiConnect, mensajes y promociones (`messagebox`), manuales (`ocean.instructions`, `repairguide`) y el feed de contenido (`waterfall.overseas`).

**Se dejó a propósito:** el sintonizador y la app de TV de TCL (`com.tcl.tv`), que también maneja las entradas HDMI, además de Chromecast (`mediashell`), AirPlay, Play Store y Ajustes.

Las apps que se usan: YouTube, Netflix, Prime Video, Crunchyroll, Apple TV, Plex, FutureTV, Spotify y SmartTube.

### 2. SmartTube para invitados

[SmartTube](https://github.com/yuliskov/SmartTube) es un cliente de YouTube para TV, sin anuncios y sin cuenta. Se instala desde su GitHub oficial (variante `armeabi-v7a`) con `./instalar-smarttube.sh`, que también sirve para actualizarlo.

### 3. Inicio: la app de pantalla de inicio (`inicio/`)

Hay que reemplazar la pantalla de inicio porque el anuncio grande de arriba (el *hero*) es parte del inicio de Google TV y no tiene ajuste para quitarlo. Inicio es una app Android pequeña (70 KB, sin dependencias) que se registra como pantalla de inicio.

- **Fondos:** fotos destacadas de naturaleza de [Wikimedia Commons](https://commons.wikimedia.org/) (montañas, lagos, bosques, playas, cascadas y costas). Son libres, no piden llave y se guardan hasta 40 en la TV, así que funciona sin internet. Se descartan por texto las que muestran trenes, pueblos o edificios. Cada foto lleva su lugar, sacado de sus coordenadas GPS con OpenStreetMap/Nominatim, su descripción y el crédito del autor con su licencia.
- **Clima:** [Open-Meteo](https://open-meteo.com/), sin llave. La ciudad sale de la IP pública ([ipapi.co](https://ipapi.co/)).
- **Dock:** las apps de arriba, más *Todas las apps*, *Entradas* (HDMI) y *Ajustes*. El orden está en `Apps.kt` → `FAVORITAS`.
- **Modo cuadro:** si no tocas el control en 45 s, el dock se esconde y queda solo el paisaje. La primera tecla lo regresa sin abrir nada.

### 4. Cuidado del panel

| Ajuste | Antes | Ahora |
|---|---|---|
| Protector de pantalla | Google `dreamx` a los 10 min | **Paisajes** (de Inicio) a los **5 min** |
| Apagar la pantalla | 19 min | **30 min** después de que arranca el protector |
| Apagado por inactividad con video | nunca | **4 h** sin tocar el control |

El fondo nunca está quieto: cada foto se acerca y se desplaza despacio, y cambia cada 3 min en el inicio y cada 75 s en el protector. En el protector, la hora cambia de esquina cada 4 min. Así ningún pixel muestra lo mismo mucho tiempo.

**Trampa de TCL:** la TV bloquea que las apps de terceros arranquen servicios solas (`TclAppBoot: callee_does't_have_OP_AUTO_START_permission`). Sin el permiso `cmd appops set dev.robertovillegas.inicio APP_AUTO_START allow`, el protector se cierra al instante. `instalar-inicio.sh` ya lo concede.

---

## Scripts

Desde la Mac, con `adb`:

| Script | Qué hace |
|---|---|
| `aplicar.sh <lista>` | Desactiva los paquetes de una lista (`fase1-desactivar.txt` o `fase2-desactivar.txt`) |
| `instalar-inicio.sh` | Compila e instala Inicio, la pone como protector y ajusta los tiempos de apagado |
| `poner-inicio.sh` | Hace de Inicio la pantalla de inicio y desactiva la de Google TV (fase 2) |
| `instalar-smarttube.sh` | Instala o actualiza SmartTube desde GitHub |
| `rescate-launcher.sh` | **Emergencia:** la TV se quedó sin pantalla de inicio. Regresa la de Google TV y reinicia |
| `restaurar-todo.sh` | Regresa todo a como venía de fábrica y reinicia |

Dentro de la TV: `adb shell sh /sdcard/restaurar-tv.sh` hace lo mismo que `restaurar-todo.sh`.

**Antes de reiniciar después de activar o desactivar apps, espera ~15 s.** Si reinicias de inmediato, PackageManager no alcanza a guardar los cambios y se revierten.

## Compilar Inicio

Necesitas JDK 21 y el SDK de Android (`brew install --cask android-commandlinetools`, luego `sdkmanager "platforms;android-35"`).

```bash
cd inicio
./gradlew assembleRelease     # app/build/outputs/apk/release/app-release.apk
```

Se firma con la llave de debug local: es una app para esta TV y no se publica en Play Store.

## Respaldo

`respaldo-original/` tiene el estado del 28 de octubre de 2026, antes de cualquier cambio: paquetes activos, de sistema y de terceros, y la pantalla de inicio por defecto. Los volcados crudos (`getprop`, `settings`, actividades preferidas) se quedan solo en la Mac porque traen seriales del equipo; están en `.gitignore`.

**Un reset de fábrica** borra `/sdcard/restaurar-tv.sh` y deja la TV como nueva, con anuncios incluidos. Para volver a dejarla así, corre `aplicar.sh fase1-desactivar.txt`, `instalar-smarttube.sh`, `instalar-inicio.sh` y `poner-inicio.sh`.
