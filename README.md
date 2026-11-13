# Invernadero – Monitoreo de ambiente para medicamentos y productos sensibles

Aplicación Android para vigilar **temperatura y humedad** en un espacio de almacenamiento
(farmacia u hospital: medicamentos y productos perecibles sensibles a la humedad) y controlar
sus equipos de corrección. Roles: **administrador** y **usuario**.

> Estado actual: el dispositivo está **simulado** dentro de la app. El diseño ya define el
> contrato de datos para conectar después un **ESP32** con sensor DHT11, ventilador,
> bomba de agua (refrigeración), bocina y LED.

## Funciones

- **Registro e inicio de sesión seguros** (Firebase Authentication): política de contraseña,
  bloqueo tras 5 intentos fallidos hasta recuperar la contraseña, cambio de contraseña,
  verificación de correo y cierre de sesión por inactividad (10 min).
- **Monitoreo en tiempo real:** temperatura, humedad y estado del dispositivo (en línea/desconectado).
- **Equipos:** bomba de agua (enfría), ventilador (baja la humedad), bocina y LED, con modo
  automático o manual por equipo.
- **Alertas tipo alarma:** pantalla de alarma, sonido y vibración, también con la app en segundo plano.
- **Protocolo de emergencia:** ante un valor crítico alto la alarma da **2 minutos** para activar el
  equipo; si nadie lo hace, el sistema lo activa solo, y la alarma se repite cada 2 minutos hasta
  que se normaliza.
- **Gráficas** de las últimas 1, 6 y 24 horas con mínimo, máximo, promedio y límites.
- **Ajustes (solo administrador):** rangos recomendados, umbrales de alarma, automatización y
  modo de cada equipo.
- **Registros de auditoría (solo administrador):** sesiones, cuentas, dispositivos, configuración y
  alertas, ordenados por categoría y día.
- **Gestión de cuentas (administrador):** cambiar rol y activar/desactivar cuentas (no se eliminan).

## Tecnologías

Kotlin · Jetpack Compose (Material 3) · MVVM · Navigation Compose · Coroutines/Flow ·
Firebase Authentication · Firebase Realtime Database.
`minSdk 28`, `targetSdk 37`.

## Estructura

```
app/src/main/java/com/collinao/invernadero/
├── alarm/           Alarma, servicio de monitoreo en segundo plano
├── data/
│   ├── model/       Modelos (lecturas, umbrales, auditoría…)
│   ├── repository/  Acceso a Firebase (auth, dispositivo, auditoría, usuarios)
│   └── simulator/   Simulador del dispositivo
├── notifications/   Notificaciones
├── security/        Política de contraseñas
└── ui/              Tema, componentes, navegación y pantallas
app/src/main/res/values/strings.xml   Todos los textos de la interfaz
database.rules.json                   Reglas de seguridad (sin comentarios)
database.comentado.json               Las mismas reglas explicadas parte por parte
PLAN_DE_PRUEBAS.md                    Casos de prueba
```

## Cómo ejecutarla

1. Abrir la carpeta en **Android Studio** y esperar la sincronización de Gradle.
2. Tener el archivo `app/google-services.json` de un proyecto de Firebase con
   **Authentication (correo/contraseña)** y **Realtime Database** activados.
3. En Firebase Console > Realtime Database > **Reglas**, pegar el contenido de
   `database.rules.json` y pulsar **Publicar**.
4. Ejecutar en un teléfono o emulador (Android 9 o superior).
5. Crear la primera cuenta desde la app. Para volverla administradora, en Realtime Database
   editar `usuarios/<uid>/perfil/rol` a `admin` (las cuentas nuevas son `usuario`).

## Seguridad

- Las reglas de Firebase hacen cumplir los permisos: solo administradores leen los registros y
  modifican umbrales; los registros no se pueden editar ni borrar.
- Limitaciones conocidas: el bloqueo por intentos se aplica desde el cliente (para hacerlo
  infalible harían falta Cloud Functions o App Check); el cierre por inactividad cuenta solo con la
  app en primer plano; la verificación de correo es informativa.

## Pruebas

- Unitarias: `app/src/test` (política de contraseñas, formato de correo y clave de bloqueo).
- Manuales: ver `PLAN_DE_PRUEBAS.md`.

## Futuro: dispositivo real (ESP32)

El ESP32 debe usar los mismos nodos que el simulador, bajo `dispositivos/<id>/`:
`sensores`, `estado`, `comandos`, `modos`, `lecturas` y `thresholds`.
