# EC Madrid Deportes

Aplicación Android nativa desarrollada en Kotlin y Jetpack Compose para consultar y gestionar calendarios de los [Juegos Deportivos de Escuelas Católicas de Madrid](https://juegosdeportivos.ecmadrid.org/Calendarios/CalendarioTemporada).

---

## 🚀 Características principales

- **Catálogo de deportes y competiciones:** Consulta de deportes activos (Baloncesto, Fútbol Sala, Voleibol) y sus competiciones asociadas para la temporada actual.
- **Paginación y búsqueda de equipos:** Navegación paginada entre los equipos de cada competición con filtro de búsqueda por texto en tiempo real.
- **Captura automática de escudos e imágenes:** Obtención y normalización de los emblemas/escudos oficiales de cada club/colegio a partir de los datos publicados, mostrándolos a la izquierda del nombre tanto en la selección como en el calendario.
- **Seguimiento multiequipo con calendarios independientes:** Permite seleccionar y guardar múltiples equipos en favoritos, gestionando y visualizando sus calendarios por separado mediante pestañas individuales.
- **Detalle de enfrentamientos:** Consulta de partidos por jornada o temporada completa, con fechas y horas formateadas, estado de la jornada (*Pendiente*, *Aplazado*, *Modificado*, *Descansa*), marcadores y ubicación de los campos de juego con enlace a Google Maps.
- **Persistencia local:** Los equipos favoritos se almacenan en el dispositivo mediante `SharedPreferences`, sin requerir registro ni envío de datos personales a servidores externos.
- **Carga concurrente:** Consulta optimizada de jornadas en paralelo mediante corrutinas de Kotlin.

---

## 🛠️ Requisitos del sistema

- **JDK:** Java 17 o superior.
- **Android SDK:** Compile SDK 35, Min SDK 26 (Android 8.0 Oreo o superior).
- **Gradle:** Wrapper incluido en el repositorio.
- **Android Studio:** Ladybug (2024.2) o superior recomendado.

---

## 💻 Instrucciones de compilación y ejecución

### Opción 1: Desde línea de comandos (Terminal)

1. **Dar permisos de ejecución al script Gradle Wrapper (Linux / macOS):**
   ```bash
   chmod +x gradlew
   ```

2. **Compilar el APK en modo depuración (Debug):**
   ```bash
   ./gradlew assembleDebug
   ```
   El archivo APK generado se ubicará en `app/build/outputs/apk/debug/app-debug.apk`.

3. **Ejecutar las pruebas unitarias:**
   ```bash
   ./gradlew testDebugUnitTest
   ```

4. **Instalar y ejecutar en un dispositivo o emulador conectado:**
   ```bash
   ./gradlew installDebug
   ```

*(En sistemas Windows, utiliza `gradlew.bat` en lugar de `./gradlew`).*

### Opción 2: Desde Android Studio

1. Abre Android Studio y selecciona **Open**.
2. Selecciona la carpeta raíz de este repositorio (`ecmadrid-apk`).
3. Espera a que termine la sincronización de Gradle (*Sync Project with Gradle Files*).
4. Selecciona la configuración de ejecución `app` y el emulador o dispositivo físico de destino.
5. Haz clic en **Run** (icono verde de reproducción o `Shift + F10`).

---

## 📄 Licencia

Este proyecto está distribuido bajo los términos de la **GNU General Public License v3.0 (GPL-3.0)**.

Puedes consultar el texto íntegro de la licencia en el archivo [LICENSE](LICENSE).

```text
This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.
```
