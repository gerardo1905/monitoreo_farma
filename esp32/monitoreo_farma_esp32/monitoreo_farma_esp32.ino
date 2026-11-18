/*
 * =====================================================================================
 *  MONITOREO FARMA – Firmware ESP32 (Unidad 3)
 *  Monitoreo de temperatura y humedad para almacenamiento de medicamentos.
 *
 *  Hardware:
 *    DHT11 (S) ............ GPIO 4
 *    Relé BOMBA (S) ....... GPIO 26  (placa negra S + -, se activa con HIGH)
 *    Relé VENTILADOR (IN) . GPIO 27  (placa azul con LEDs, se activa con LOW)
 *    LED rojo ............. GPIO 23
 *    Buzzer pasivo ........ GPIO 25
 *
 *  Comunicación: WiFi + Firebase Realtime Database (HTTPS/TLS) con la app Android
 *  "monitoreo_farma". Usa EXACTAMENTE los mismos nodos que la app:
 *    dispositivos/<DEVICE_ID>/sensores    {temperatura, humedadAire, timestamp}
 *    dispositivos/<DEVICE_ID>/estado      {online, ultimaConexion, rssi, pendientes, firmware}
 *    dispositivos/<DEVICE_ID>/comandos    {bomba, ventilador, bocina, led}   (true/false)
 *    dispositivos/<DEVICE_ID>/modos       {bomba, ventilador, bocina, led}   ("auto"/"manual")
 *    dispositivos/<DEVICE_ID>/thresholds  {...}  (solo lectura para el ESP32)
 *    dispositivos/<DEVICE_ID>/lecturas/<0..1439>  historial de 24 h (1 por minuto)
 *    registros/<categoria>/<push>         bitácora de auditoría
 *
 *  Indicadores de la rúbrica cubiertos (buscar el código en los comentarios):
 *    3.1.1.1  Envío seguro de información (TLS + autenticación + credenciales separadas)
 *    3.1.1.2  Integridad de los datos (firma HMAC-SHA256, validación, CRC32, hora del servidor)
 *    3.1.1.3  Reconexión automática WiFi / Firebase
 *    3.1.1.4  Almacenamiento temporal en el microcontrolador (LittleFS) y sincronización
 *    3.1.3.10 Optimización del consumo de energía
 *    3.1.4.11 Seguridad según ISO 27001 (mínimo privilegio, auditoría, sin secretos en el código)
 *    3.1.4.12 Protección de datos (no se imprimen ni envían credenciales ni datos personales)
 *    3.1.4.13 Plan de contingencia y continuidad operativa (modo autónomo sin conexión)
 *
 *  Librerías (Arduino IDE > Gestor de librerías):
 *    - "Firebase Arduino Client Library for ESP8266 and ESP32" de Mobizt (Firebase_ESP_Client)
 *    - "DHT sensor library" de Adafruit (+ "Adafruit Unified Sensor")
 *  Placa: "ESP32 Dev Module" (paquete esp32 de Espressif, versión 2.x o 3.x).
 * =====================================================================================
 */

#include <Arduino.h>
#if __has_include(<esp_arduino_version.h>)
#include <esp_arduino_version.h>
#endif
#ifndef ESP_ARDUINO_VERSION_MAJOR
#define ESP_ARDUINO_VERSION_MAJOR 2
#endif

#include <WiFi.h>
#include <time.h>
#include <math.h>
#include <Preferences.h>
#include <LittleFS.h>
#include <esp_task_wdt.h>
#include <DHT.h>
#include <Firebase_ESP_Client.h>
#include "addons/TokenHelper.h"
#include "mbedtls/md.h"            // HMAC-SHA256 por hardware/software del ESP32

// 3.1.1.1 / 3.1.4.11: las credenciales NO están en el código fuente. Viven en secrets.h,
// que está en .gitignore y nunca se sube al repositorio (ver secrets_ejemplo.h).
#include "secrets.h"

// =====================================================================================
//  CONFIGURACIÓN
// =====================================================================================
#define FIRMWARE_VERSION "1.0.0"
#define DEVICE_ID        "esp32-001"      // Debe coincidir con DeviceConfig.DEVICE_ID de la app

// --- Pines ---
#define PIN_DHT             4
#define PIN_RELE_BOMBA      26
#define PIN_RELE_VENTILADOR 27
#define PIN_LED             23
#define PIN_BUZZER          25
#define DHT_TIPO            DHT11

// --- Lógica de los relés (si un equipo funciona al revés, cambia solo su true/false) ---
#define BOMBA_ACTIVO_EN_LOW       false   // placa negra (S + -)  -> se activa con HIGH
#define VENTILADOR_ACTIVO_EN_LOW  true    // placa azul (LEDs)    -> se activa con LOW

// --- Tiempos (ms) ---
const unsigned long CICLO_CONTROL_MS          = 2500;    // lectura del DHT11 + control (el DHT11 necesita >= 2 s)
const unsigned long INTERVALO_UMBRALES_MS     = 15000;   // relectura de umbrales
const unsigned long INTERVALO_LATIDO_MS       = 10000;   // latido "en línea" (la app exige < 15 s)
const unsigned long INTERVALO_SENSORES_MAX_MS = 30000;   // envío de sensores aunque no cambien
const unsigned long INTERVALO_LECTURA_MS      = 60000;   // 1 lectura por minuto al historial
const unsigned long EMERGENCIA_ESPERA_MS      = 120000;  // 2 min para atender la alarma (igual que la app)
const unsigned long SONDEO_SIN_SERVIDOR_MS    = 10000;   // con WiFi pero sin servidor: 1 intento cada 10 s
const unsigned long WIFI_REINTENTO_MIN_MS     = 5000;    // reconexión WiFi con espera creciente...
const unsigned long WIFI_REINTENTO_MAX_MS     = 60000;   // ...hasta 1 minuto entre intentos
const unsigned long REINICIO_SIN_WIFI_MS      = 30UL * 60UL * 1000UL;  // 30 min sin WiFi -> reinicio
const uint32_t      WDT_TIMEOUT_S             = 60;      // watchdog: reinicia si el programa se cuelga

// --- Automatización (mismos valores que usaba el simulador de la app) ---
const float MARGEN_BOMBA      = 1.0;   // histéresis: la bomba se apaga 1 °C bajo el máximo
const float MARGEN_VENTILADOR = 5.0;   // el ventilador se apaga 5 % bajo el máximo

// --- Validación del sensor (3.1.1.2) ---
const float CAMBIO_MIN_TEMP  = 0.5;    // solo se envía si cambia al menos esto (ahorro de energía)
const float CAMBIO_MIN_HUM   = 1.0;
const float SALTO_MAX_TEMP   = 8.0;    // un salto mayor entre dos lecturas se considera ruido
const float SALTO_MAX_HUM    = 20.0;
const int   FALLAS_SENSOR_MAX = 5;     // lecturas fallidas seguidas para declarar el sensor en falla

// --- Almacenamiento temporal (3.1.1.4) ---
const int   MAX_PENDIENTES   = 1440;   // 24 h de lecturas (1 por minuto)
const int   LOTE_SUBIDA      = 10;     // lecturas que se suben por petición al reconectar
const char* ARCHIVO_PENDIENTES = "/pendientes.bin";

// --- Firma HMAC-SHA256 (3.1.1.1 / 3.1.1.2) ---
const uint64_t TOLERANCIA_FUTURO_MS = 5UL * 60UL * 1000UL;   // se rechazan firmas "del futuro" (> 5 min)

// =====================================================================================
//  ESTADO
// =====================================================================================
enum Actuador { BOMBA = 0, VENTILADOR, BOCINA, LED_IND, N_ACT };
const char* ACT_ID[N_ACT]     = { "bomba", "ventilador", "bocina", "led" };
const char* ACT_NOMBRE[N_ACT] = { "Bomba de agua", "Ventilador", "Bocina", "LED indicador" };

bool estadoAct[N_ACT] = { false, false, false, false };   // encendido / apagado
bool manualAct[N_ACT] = { false, false, false, false };   // true = modo manual (lo maneja el usuario)

struct Umbrales {
  float tMax = 25, tMin = 15, hMax = 60, hMin = 30;        // rango normal (automatización)
  bool  automatizacion = true;
  float aTMax = 30, aTMin = 10, aHMax = 80, aHMin = 20;    // zona crítica (alarma)
} u;

// Registro guardado en la memoria flash cuando no hay conexión (20 bytes)
struct RegistroPendiente {
  uint32_t epochSeg;       // hora real (0 = aún sin hora de internet)
  uint32_t millisCaptura;  // millis() al capturar (para calcular la hora después)
  float    temperatura;
  float    humedad;
  uint32_t crc;            // 3.1.1.2: CRC32 de los 16 bytes anteriores
};
RegistroPendiente pendientes[MAX_PENDIENTES];
int nPendientes = 0;

// Sensor
DHT dht(PIN_DHT, DHT_TIPO);
float temperatura = NAN, humedad = NAN;
float tEnviada = NAN, hEnviada = NAN;
bool  sensorOk = false;
int   fallasSensor = 0, saltosSeguidos = 0;

// Emergencia (por equipo: bomba = temperatura, ventilador = humedad)
bool          criticoActivo[N_ACT] = { false };
unsigned long criticoDesde[N_ACT]  = { 0 };
bool          forzado[N_ACT]       = { false };
bool          alertaCriticaActiva  = false;

// Conectividad
bool          wifiConectado = false;
bool          servidorOk = false;          // última petición a Firebase exitosa
bool          estabaEnLinea = false;
unsigned long ultimoWifiOk = 0, ultimoIntentoWifi = 0, esperaWifi = WIFI_REINTENTO_MIN_MS;
unsigned long desconectadoDesde = 0, ultimoSondeo = 0;
uint32_t      reconexiones = 0;
bool          horaConfigurada = false;

// Temporizadores
unsigned long tCiclo = 0, tUmbrales = 0, tLatido = 0, tSensores = 0, tLectura = 0;
bool umbralesLeidos = false;

// Firma: último "ts" aceptado de la app por equipo (anti-repetición) y de los umbrales
uint64_t ultimoTsApp[N_ACT] = { 0 };
uint64_t ultimoTsUmbrales = 0;
String   ultimoUmbralRechazado = "";

// Firebase
FirebaseData   fbdo;
FirebaseAuth   auth;
FirebaseConfig config;
const String RUTA = String("/dispositivos/") + DEVICE_ID;

Preferences prefs;

// Declaraciones adelantadas
bool enLinea();
bool peticionOk(bool ok);
void auditar(const char* categoria, const String& tipo, const String& detalle);
void guardarPendientesEnFlash();
void cambiarActuador(int id, bool encendido, bool volverAuto = false);
void publicarActuador(int id, bool incluirModo = true);

// =====================================================================================
//  UTILIDADES
// =====================================================================================
void logMsg(const String& m) {
  Serial.printf("[%8lu] %s\n", millis() / 1000, m.c_str());
}

float redondear1(float v) { return roundf(v * 10.0f) / 10.0f; }

bool horaValida() { return time(nullptr) > 1700000000; }

uint64_t ahoraMs() { return (uint64_t)time(nullptr) * 1000ULL; }

// 3.1.1.2 Integridad: CRC32 para detectar registros corruptos en la memoria flash
uint32_t crc32(const uint8_t* datos, size_t n) {
  uint32_t c = 0xFFFFFFFF;
  for (size_t i = 0; i < n; i++) {
    c ^= datos[i];
    for (int k = 0; k < 8; k++) c = (c >> 1) ^ (0xEDB88320 & (-(int32_t)(c & 1)));
  }
  return ~c;
}
uint32_t crcRegistro(const RegistroPendiente& r) {
  return crc32((const uint8_t*)&r, sizeof(RegistroPendiente) - sizeof(uint32_t));
}

// =====================================================================================
//  3.1.1.1 / 3.1.1.2  FIRMA HMAC-SHA256 DE LA INFORMACIÓN EN TRÁNSITO
//  La app firma cada comando y cada cambio de umbrales con una clave secreta compartida
//  (HMAC_KEY en secrets.h / local.properties). El ESP32 recalcula el hash y solo obedece
//  si coincide: un dato alterado en la base de datos sin la clave se rechaza.
// =====================================================================================
String hmacSha256(const String& mensaje) {
  uint8_t salida[32];
  const mbedtls_md_info_t* info = mbedtls_md_info_from_type(MBEDTLS_MD_SHA256);
  mbedtls_md_hmac(info, (const unsigned char*)HMAC_KEY, strlen(HMAC_KEY),
                  (const unsigned char*)mensaje.c_str(), mensaje.length(), salida);
  char hex[65];
  for (int i = 0; i < 32; i++) sprintf(hex + i * 2, "%02x", salida[i]);
  hex[64] = 0;
  return String(hex);
}

// Comparación en tiempo constante (no revela por cuánto difieren dos hashes)
bool hashIguales(const String& a, const String& b) {
  if (a.length() != b.length()) return false;
  uint8_t dif = 0;
  for (unsigned int i = 0; i < a.length(); i++) dif |= (uint8_t)(a[i] ^ b[i]);
  return dif == 0;
}

uint64_t aEntero64(const String& s) { return strtoull(s.c_str(), nullptr, 10); }

String u64aTexto(uint64_t v) {
  char b[21];
  snprintf(b, sizeof(b), "%llu", (unsigned long long)v);
  return String(b);
}

// Mismo texto canónico que IntegridadDatos.kt: "<dispositivo>|<equipo>|<1/0>|<auto/manual>|<ts>"
String mensajeComando(int id, bool valor, const String& modo, const String& ts) {
  return String(DEVICE_ID) + "|" + ACT_ID[id] + "|" + (valor ? "1" : "0") + "|" + modo + "|" + ts;
}

// Igual que roundToInt() de Kotlin: los decimales se firman como enteros (valor x 100)
long cent(float v) { return (long)floorf(v * 100.0f + 0.5f); }

String mensajeUmbrales(const Umbrales& n, const String& ts) {
  char b[200];
  snprintf(b, sizeof(b), "%s|thresholds|%ld|%ld|%ld|%ld|%d|%ld|%ld|%ld|%ld|%s", DEVICE_ID,
           cent(n.tMax), cent(n.tMin), cent(n.hMax), cent(n.hMin), n.automatizacion ? 1 : 0,
           cent(n.aTMax), cent(n.aTMin), cent(n.aHMax), cent(n.aHMin), ts.c_str());
  return String(b);
}

// Nivel eléctrico del relé según su lógica (activo en LOW o en HIGH)
int nivelRele(bool encendido, bool activoEnLow) { return (encendido != activoEnLow) ? HIGH : LOW; }

// ----- Buzzer pasivo (PWM). Compatible con el core ESP32 2.x y 3.x -----
void buzzerIniciar() {
#if ESP_ARDUINO_VERSION_MAJOR >= 3
  ledcAttach(PIN_BUZZER, 2000, 8);
#else
  ledcSetup(0, 2000, 8);
  ledcAttachPin(PIN_BUZZER, 0);
#endif
}
void tono(uint32_t frecuencia) {
#if ESP_ARDUINO_VERSION_MAJOR >= 3
  ledcWriteTone(PIN_BUZZER, frecuencia);
#else
  ledcWriteTone(0, frecuencia);
#endif
}

// ----- FALLA SEGURA (fail-safe) #3: watchdog. Si el programa se cuelga más de 60 s,
//       el ESP32 se reinicia solo y vuelve a arrancar con los relés apagados. -----
void watchdogIniciar() {
#if ESP_ARDUINO_VERSION_MAJOR >= 3
  esp_task_wdt_config_t cfg = { .timeout_ms = WDT_TIMEOUT_S * 1000, .idle_core_mask = 0, .trigger_panic = true };
  if (esp_task_wdt_init(&cfg) == ESP_ERR_INVALID_STATE) esp_task_wdt_reconfigure(&cfg);
#else
  esp_task_wdt_init(WDT_TIMEOUT_S, true);
#endif
  esp_task_wdt_add(NULL);
}

// =====================================================================================
//  SALIDAS (relés, LED, buzzer)
// =====================================================================================
void aplicarSalidas() {
  digitalWrite(PIN_RELE_BOMBA,      nivelRele(estadoAct[BOMBA],      BOMBA_ACTIVO_EN_LOW));
  digitalWrite(PIN_RELE_VENTILADOR, nivelRele(estadoAct[VENTILADOR], VENTILADOR_ACTIVO_EN_LOW));
}

// LED y buzzer se actualizan en cada vuelta del loop (patrones sin delay)
void actualizarIndicadores() {
  unsigned long t = millis();

  // LED: encendido fijo en advertencia; parpadeo rápido si el sensor falló (contingencia)
  bool led = estadoAct[LED_IND];
  if (!sensorOk && fallasSensor >= FALLAS_SENSOR_MAX) led = (t / 250) % 2;
  digitalWrite(PIN_LED, led ? HIGH : LOW);

  // Buzzer pasivo: sirena de dos tonos solo cuando la bocina está encendida (ahorro: apagado el resto)
  static uint32_t ultimoTono = 0;
  uint32_t f = 0;
  if (estadoAct[BOCINA]) {
    unsigned long fase = t % 800;
    f = fase < 300 ? 2500 : (fase < 600 ? 1800 : 0);
  }
  if (f != ultimoTono) { tono(f); ultimoTono = f; }
}

// Cambia un actuador. publicar=true lo escribe en Firebase (comandos y, si se pide, modo "auto").
void cambiarActuador(int id, bool encendido, bool volverAuto) {
  estadoAct[id] = encendido;
  if (volverAuto) manualAct[id] = false;
  aplicarSalidas();
  prefs.putBool((String("c_") + ACT_ID[id]).c_str(), encendido);
  if (volverAuto) prefs.putBool((String("m_") + ACT_ID[id]).c_str(), false);

  // FALLA SEGURA (fail-safe) #4: sin conexión el ESP32 sigue controlando los equipos en local
  // con los últimos umbrales guardados (modo autónomo) y se sincroniza al reconectar.
  if (!enLinea()) return;
  publicarActuador(id, volverAuto);
}

// Escribe en Firebase el estado y el modo locales de un equipo, firmados por el ESP32
void publicarActuador(int id, bool incluirModo) {
  String modo = manualAct[id] ? "manual" : "auto";
  if (horaValida()) {
    String ts = u64aTexto(ahoraMs());
    FirebaseJson f;
    f.set("valor", estadoAct[id]);
    f.set("modo", modo);
    f.set("ts", ts);
    f.set("origen", "esp32");
    f.set("hash", hmacSha256(mensajeComando(id, estadoAct[id], modo, ts)));
    peticionOk(Firebase.RTDB.setJSON(&fbdo, RUTA + "/firmas/" + ACT_ID[id], &f));
  }
  FirebaseJson c;
  c.set(ACT_ID[id], estadoAct[id]);
  peticionOk(Firebase.RTDB.updateNode(&fbdo, RUTA + "/comandos", &c));
  if (!incluirModo) return;   // 3.1.3.10: el modo solo se reescribe si cambió (una petición menos)
  FirebaseJson m;
  m.set(ACT_ID[id], modo);
  peticionOk(Firebase.RTDB.updateNode(&fbdo, RUTA + "/modos", &m));
}

// =====================================================================================
//  3.1.1.3  RECONEXIÓN AUTOMÁTICA (WiFi y Firebase)
// =====================================================================================
bool enLinea() {
  return wifiConectado && Firebase.ready() && servidorOk;
}

// Registra el resultado de cada petición. 3 fallas seguidas = servidor no disponible.
bool peticionOk(bool ok) {
  static int fallasSeguidas = 0;
  if (ok) {
    fallasSeguidas = 0;
    servidorOk = true;
  } else {
    logMsg("Firebase error: " + fbdo.errorReason());
    if (++fallasSeguidas >= 3) servidorOk = false;
  }
  return ok;
}

void wifiIniciar() {
  WiFi.mode(WIFI_STA);
  WiFi.persistent(false);          // no se guardan credenciales extra en la flash
  WiFi.setAutoReconnect(true);     // 3.1.1.3: el propio WiFi reintenta
  WiFi.setSleep(true);             // 3.1.3.10: modem-sleep entre transmisiones
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  ultimoIntentoWifi = millis();
  logMsg("Conectando a WiFi...");
}

void wifiVigilar() {
  unsigned long ahora = millis();

  if (WiFi.status() == WL_CONNECTED) {
    if (!wifiConectado) {
      wifiConectado = true;
      esperaWifi = WIFI_REINTENTO_MIN_MS;
      logMsg("WiFi conectado. IP " + WiFi.localIP().toString() + "  RSSI " + String(WiFi.RSSI()) + " dBm");
      if (!horaConfigurada) {
        // Hora de internet (UTC) para el historial y las lecturas guardadas sin conexión
        configTime(0, 0, "pool.ntp.org", "time.google.com");
        horaConfigurada = true;
      }
      servidorOk = true;   // se confirma con la próxima petición
    }
    ultimoWifiOk = ahora;
    return;
  }

  if (wifiConectado) {
    wifiConectado = false;
    logMsg("WiFi perdido. Se activa el modo autónomo y el almacenamiento local.");
  }

  // RECONEXIÓN AUTOMÁTICA #1: reintento con espera creciente (5 s, 10 s, 20 s... máx 60 s),
  // reconecta sin gastar batería.
  if (ahora - ultimoIntentoWifi >= esperaWifi) {
    logMsg("Reintentando WiFi (espera " + String(esperaWifi / 1000) + " s)...");
    WiFi.disconnect(false);
    WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
    ultimoIntentoWifi = ahora;
    esperaWifi = min(esperaWifi * 2, WIFI_REINTENTO_MAX_MS);
  }

  // RECONEXIÓN AUTOMÁTICA #2 (último recurso): 30 min sin WiFi -> reinicio. Las lecturas pendientes
  // están en la flash y no se pierden.
  if (ahora - ultimoWifiOk > REINICIO_SIN_WIFI_MS) {
    logMsg("30 min sin WiFi: reiniciando el ESP32...");
    guardarPendientesEnFlash();
    ESP.restart();
  }
}

// RECONEXIÓN AUTOMÁTICA #3: detecta el paso a "en línea" / "fuera de línea", relee la configuración
// y deja registro en la auditoría (DISPOSITIVO_EN_LINEA).
void vigilarTransiciones() {
  bool ahora = enLinea();
  if (ahora && !estabaEnLinea) {
    reconexiones++;
    unsigned long fuera = desconectadoDesde ? (millis() - desconectadoDesde) / 1000 : 0;
    logMsg("EN LÍNEA con Firebase (conexión #" + String(reconexiones) + ")");
    tUmbrales = 0;          // relee umbrales de inmediato
    tLatido = 0;            // avisa a la app de inmediato
    String detalle = reconexiones == 1
      ? String("ESP32 " DEVICE_ID " conectado (firmware " FIRMWARE_VERSION ")")
      : "ESP32 reconectado automáticamente tras " + String(fuera) + " s sin conexión; " +
        String(nPendientes) + " lecturas guardadas por sincronizar";
    auditar("dispositivos", "DISPOSITIVO_EN_LINEA", detalle);
  } else if (!ahora && estabaEnLinea) {
    desconectadoDesde = millis();
    logMsg("FUERA DE LÍNEA: el ESP32 sigue controlando los equipos solo y guarda las lecturas en la flash.");
  }
  estabaEnLinea = ahora;
}

// =====================================================================================
//  3.1.1.1  FIREBASE: CONEXIÓN SEGURA
// =====================================================================================
void firebaseIniciar() {
  // Conexión cifrada HTTPS/TLS con Firebase (el cliente usa SSL en todas las peticiones).
  // Autenticación con una cuenta propia del ESP32 (rol "usuario", sin privilegios de admin) y
  // autorizada SOLO para este dispositivo en las reglas: dispositivosAutorizados/<DEVICE_ID>/<uid>.
  config.api_key = API_KEY;
  config.database_url = DATABASE_URL;
  auth.user.email = DEVICE_EMAIL;
  auth.user.password = DEVICE_PASSWORD;
  config.token_status_callback = tokenStatusCallback;   // RECONEXIÓN AUTOMÁTICA #5: renueva el token de acceso solo
  config.timeout.serverResponse = 10 * 1000;

  Firebase.reconnectNetwork(false);    // el WiFi lo reconecta wifiVigilar() con espera creciente
  fbdo.setBSSLBufferSize(4096, 1024);
  fbdo.setResponseSize(2048);
  Firebase.begin(&config, &auth);
  logMsg("Firebase iniciado para " DEVICE_ID " (las credenciales no se muestran por seguridad)");
}

// Bitácora de auditoría (misma estructura que la app). 3.1.4.11: trazabilidad de eventos.
void auditar(const char* categoria, const String& tipo, const String& detalle) {
  if (!enLinea()) return;
  FirebaseJson j;
  j.set("tipo", tipo);
  j.set("detalle", detalle.substring(0, 300));
  j.set("usuario", "Sistema");
  j.set("uid", auth.token.uid.c_str());          // las reglas exigen el uid real de quien escribe
  j.set("timestamp/.sv", "timestamp");           // hora del servidor: no se puede falsear
  peticionOk(Firebase.RTDB.pushJSON(&fbdo, String("/registros/") + categoria, &j));
}

// =====================================================================================
//  LECTURA DE COMANDOS, MODOS Y UMBRALES DESDE LA APP
// =====================================================================================
bool leerJson(const String& ruta, FirebaseJson& destino, bool& vacio) {
  vacio = false;
  if (!peticionOk(Firebase.RTDB.get(&fbdo, ruta))) return false;
  if (fbdo.dataType() != "json") { vacio = true; return true; }   // nodo vacío (null)
  destino.setJsonData(fbdo.jsonString());
  return true;
}

// Verifica la firma HMAC de un cambio que llegó desde Firebase para el equipo [i]
bool firmaComandoValida(FirebaseJson& firmas, bool hayFirmas, int i, bool valor, const String& modo, String& motivo) {
  if (!hayFirmas) { motivo = "sin firma"; return false; }
  FirebaseJsonData r;
  String base = String(ACT_ID[i]) + "/";
  if (!firmas.get(r, base + "hash")) { motivo = "sin firma"; return false; }
  String hash = r.to<String>();
  if (!firmas.get(r, base + "ts")) { motivo = "firma sin hora"; return false; }
  String ts = r.to<String>();
  String origen = firmas.get(r, base + "origen") ? r.to<String>() : "";
  bool fValor = firmas.get(r, base + "valor") && r.to<bool>();
  String fModo = firmas.get(r, base + "modo") ? r.to<String>() : "";

  if (fValor != valor || fModo != modo) { motivo = "la firma no corresponde al dato"; return false; }
  if (!hashIguales(hmacSha256(mensajeComando(i, valor, modo, ts)), hash)) { motivo = "hash HMAC no coincide (dato alterado)"; return false; }

  uint64_t t = aEntero64(ts);
  if (horaValida() && t > ahoraMs() + TOLERANCIA_FUTURO_MS) { motivo = "firma con hora futura"; return false; }
  if (origen == "app") {
    if (t <= ultimoTsApp[i]) { motivo = "firma repetida (posible reenvío)"; return false; }
    ultimoTsApp[i] = t;
    prefs.putULong64((String("ts_") + ACT_ID[i]).c_str(), t);
  }
  return true;
}

void leerComandosYModos() {
  FirebaseJson c, m, firmas;
  bool vacioC, vacioM, vacioF = true, firmasLeidas = false;
  if (!leerJson(RUTA + "/comandos", c, vacioC)) return;
  if (!leerJson(RUTA + "/modos", m, vacioM)) return;

  FirebaseJsonData r;
  for (int i = 0; i < N_ACT; i++) {
    bool nuevoEstado = estadoAct[i];
    bool nuevoManual = false;
    if (!vacioC && c.get(r, ACT_ID[i]) && r.type == "boolean") nuevoEstado = r.to<bool>();
    if (!vacioM && m.get(r, ACT_ID[i])) nuevoManual = (r.to<String>() == "manual");
    if (nuevoEstado == estadoAct[i] && nuevoManual == manualAct[i]) continue;   // sin cambios

    // Hubo un cambio desde fuera: se lee el nodo de firmas (solo cuando hace falta: ahorra energía)
    if (!firmasLeidas) {
      firmasLeidas = true;
      if (!leerJson(RUTA + "/firmas", firmas, vacioF)) return;
    }
    String motivo;
    if (!firmaComandoValida(firmas, !vacioF, i, nuevoEstado, nuevoManual ? "manual" : "auto", motivo)) {
      // FALLA SEGURA (fail-safe) #5: un comando con firma inválida NO se ejecuta; el equipo mantiene
      // su estado y se restaura en Firebase (firmado por el ESP32). 3.1.1.2
      logMsg(String("COMANDO RECHAZADO para ") + ACT_NOMBRE[i] + ": " + motivo);
      auditar("dispositivos", "COMANDO_RECHAZADO",
              String("El ESP32 rechazó un cambio de ") + ACT_NOMBRE[i] + " (" + motivo + ") y restauró su estado");
      publicarActuador(i);
      continue;
    }

    if (nuevoManual != manualAct[i]) {
      manualAct[i] = nuevoManual;
      prefs.putBool((String("m_") + ACT_ID[i]).c_str(), nuevoManual);
      logMsg(String(ACT_NOMBRE[i]) + " en modo " + (nuevoManual ? "MANUAL" : "AUTOMÁTICO") + " (firma verificada)");
    }
    if (nuevoEstado != estadoAct[i]) {
      estadoAct[i] = nuevoEstado;
      prefs.putBool((String("c_") + ACT_ID[i]).c_str(), nuevoEstado);
      logMsg(String("Comando desde la app: ") + ACT_NOMBRE[i] + (nuevoEstado ? " ON" : " OFF") + " (firma HMAC verificada)");
    }
  }
  aplicarSalidas();
}

float numero(FirebaseJson& j, const char* clave, float porDefecto) {
  FirebaseJsonData r;
  if (j.get(r, clave) && (r.type == "int" || r.type == "float" || r.type == "double")) return r.to<float>();
  return porDefecto;
}

void leerUmbrales() {
  FirebaseJson j;
  bool vacio;
  if (!leerJson(RUTA + "/thresholds", j, vacio)) return;
  umbralesLeidos = true;
  if (vacio) {
    logMsg("Sin umbrales en Firebase: se usan los valores por defecto (guárdalos desde Ajustes en la app).");
    return;
  }
  Umbrales n;
  n.tMax  = numero(j, "temperaturaMax", u.tMax);
  n.tMin  = numero(j, "temperaturaMin", u.tMin);
  n.hMax  = numero(j, "humedadAireMax", u.hMax);
  n.hMin  = numero(j, "humedadAireMin", u.hMin);
  n.aTMax = numero(j, "alertaTemperaturaMax", u.aTMax);
  n.aTMin = numero(j, "alertaTemperaturaMin", u.aTMin);
  n.aHMax = numero(j, "alertaHumedadMax", u.aHMax);
  n.aHMin = numero(j, "alertaHumedadMin", u.aHMin);
  FirebaseJsonData r;
  n.automatizacion = (j.get(r, "automatizacionActiva") && r.type == "boolean") ? r.to<bool>() : u.automatizacion;

  // FALLA SEGURA (fail-safe) #6: umbrales incoherentes (mínimo >= máximo) se descartan y se
  // mantienen los últimos válidos. 3.1.1.2
  if (n.tMin >= n.tMax || n.hMin >= n.hMax || n.aTMin >= n.aTMax || n.aHMin >= n.aHMax) {
    logMsg("Umbrales incoherentes recibidos: se mantienen los anteriores.");
    return;
  }
  bool distintos = n.tMax != u.tMax || n.tMin != u.tMin || n.hMax != u.hMax || n.hMin != u.hMin ||
                   n.aTMax != u.aTMax || n.aTMin != u.aTMin || n.aHMax != u.aHMax || n.aHMin != u.aHMin ||
                   n.automatizacion != u.automatizacion;
  if (distintos) {
    // 3.1.1.2: los umbrales nuevos solo se aceptan con una firma HMAC válida enviada por la app
    FirebaseJson f;
    bool vacioF;
    if (!leerJson(RUTA + "/firmas/thresholds", f, vacioF)) return;
    String hash, ts, motivo;
    FirebaseJsonData r2;
    if (vacioF || !f.get(r2, "hash")) motivo = "sin firma";
    else {
      hash = r2.to<String>();
      ts = f.get(r2, "ts") ? r2.to<String>() : "";
      uint64_t tsNum = aEntero64(ts);
      if (!hashIguales(hmacSha256(mensajeUmbrales(n, ts)), hash)) motivo = "hash HMAC no coincide (dato alterado)";
      else if (horaValida() && tsNum > ahoraMs() + TOLERANCIA_FUTURO_MS) motivo = "firma con hora futura";
      else if (tsNum <= ultimoTsUmbrales) motivo = "firma repetida (posible reenvío)";
      else {
        ultimoTsUmbrales = tsNum;
        prefs.putULong64("ts_umbrales", tsNum);
      }
    }
    if (motivo.length() > 0) {
      String huella = hash + ts + motivo;
      if (huella != ultimoUmbralRechazado) {      // se informa una sola vez por cada dato rechazado
        ultimoUmbralRechazado = huella;
        logMsg("UMBRALES RECHAZADOS: " + motivo + ". Se mantienen los anteriores.");
        auditar("configuracion", "UMBRALES_RECHAZADOS",
                "El ESP32 rechazó umbrales recibidos (" + motivo + "); guárdalos de nuevo desde Ajustes en la app");
      }
      return;
    }
    u = n;
    // 3.1.4.13: se guardan en la flash para seguir funcionando sin conexión (y tras un reinicio)
    prefs.putBytes("umbrales", &u, sizeof(Umbrales));
    logMsg("Umbrales actualizados (firma HMAC verificada): bomba > " + String(u.tMax, 1) + " °C, ventilador > " + String(u.hMax, 0) +
        " %, alarma > " + String(u.aTMax, 1) + " °C / " + String(u.aHMax, 0) + " %");
  }
}

// =====================================================================================
//  3.1.1.2  LECTURA Y VALIDACIÓN DEL SENSOR
// =====================================================================================
bool fallaSensor(const String& motivo) {
  fallasSensor++;
  logMsg("Lectura DHT11 descartada: " + motivo);
  if (fallasSensor == FALLAS_SENSOR_MAX && sensorOk) {
    sensorOk = false;
    logMsg("SENSOR EN FALLA: equipos automáticos en estado seguro y LED parpadeando.");
    auditar("alertas", "SENSOR_FALLA", "El sensor DHT11 no responde (" + motivo + "); equipos automáticos apagados");
  }
  return false;
}

bool leerSensor() {
  float h = dht.readHumidity();
  float t = dht.readTemperature();

  if (isnan(h) || isnan(t)) return fallaSensor("sin respuesta (NaN)");
  if (t < 0 || t > 50 || h < 5 || h > 95) return fallaSensor("fuera del rango del DHT11");

  // Un salto brusco aislado es ruido; si se repite 3 veces seguidas, es real y se acepta
  if (!isnan(temperatura) && (fabsf(t - temperatura) > SALTO_MAX_TEMP || fabsf(h - humedad) > SALTO_MAX_HUM)) {
    if (++saltosSeguidos < 3) return fallaSensor("salto brusco");
  }
  saltosSeguidos = 0;

  if (!sensorOk && fallasSensor >= FALLAS_SENSOR_MAX) {
    auditar("alertas", "SENSOR_RECUPERADO", "El sensor DHT11 vuelve a entregar lecturas válidas");
    logMsg("Sensor recuperado.");
  }
  fallasSensor = 0;
  sensorOk = true;
  temperatura = redondear1(t);
  humedad = redondear1(h);
  return true;
}

// =====================================================================================
//  AUTOMATIZACIÓN Y PROTOCOLO DE EMERGENCIA (misma lógica que tenía el simulador de la app)
//  - Bomba de agua: se enciende cuando la TEMPERATURA supera temperaturaMax (enfría).
//  - Ventilador: se enciende cuando la HUMEDAD supera humedadAireMax (seca).
//  - LED: advertencia, cualquier valor fuera del rango normal.
//  - Bocina (buzzer): zona crítica, cualquier valor fuera de los umbrales de alarma.
//  Respeta el modo de cada equipo: en "manual" manda el usuario desde la app.
// =====================================================================================
bool enZonaCritica() {
  return temperatura > u.aTMax || temperatura < u.aTMin || humedad > u.aHMax || humedad < u.aHMin;
}

void emergenciaEquipo(int eq, bool critico, bool normalizado, const String& descripcion) {
  unsigned long ahora = millis();
  if (critico) {
    if (!criticoActivo[eq]) { criticoActivo[eq] = true; criticoDesde[eq] = ahora; }
    if (!forzado[eq] && estadoAct[eq]) {
      forzado[eq] = true;   // el usuario ya lo encendió desde la alarma: el protocolo lo apagará al normalizarse
    } else if (!forzado[eq] && ahora - criticoDesde[eq] >= EMERGENCIA_ESPERA_MS) {
      forzado[eq] = true;
      logMsg("EMERGENCIA: 2 min sin atender, se enciende " + String(ACT_NOMBRE[eq]));
      auditar("alertas", "EMERGENCIA_AUTOMATICA",
              "La alarma no se atendió en 2 minutos: " + String(ACT_NOMBRE[eq]) + " activado automáticamente (" + descripcion + ")");
    }
  } else {
    criticoActivo[eq] = false;
  }

  if (forzado[eq]) {
    if (!critico && normalizado) {
      forzado[eq] = false;
      cambiarActuador(eq, false, true);   // se apaga y vuelve a modo automático
      auditar("alertas", "EMERGENCIA_RESUELTA", "Valores normalizados: se apagó " + String(ACT_NOMBRE[eq]) + " de emergencia");
    } else if (!estadoAct[eq]) {
      cambiarActuador(eq, true);
    }
  }
}

void controlar() {
  // FALLA SEGURA (fail-safe) #2: si el sensor DHT11 falla, los equipos automáticos se APAGAN
  // (no se actúa a ciegas) y el LED parpadea para avisar en el lugar. 3.1.4.13 Contingencia.
  if (!sensorOk) {
    for (int i = 0; i < N_ACT; i++) {
      if (!manualAct[i] && estadoAct[i]) cambiarActuador(i, false);
    }
    return;
  }

  // FALLA SEGURA (fail-safe) #7: protocolo de emergencia. Si nadie atiende la alarma en 2 min,
  // el ESP32 enciende el equipo solo, aunque la automatización esté desactivada.
  emergenciaEquipo(BOMBA, temperatura > u.aTMax, temperatura <= u.tMax, "temp " + String(temperatura, 1) + " °C");
  emergenciaEquipo(VENTILADOR, humedad > u.aHMax, humedad <= u.hMax, "humedad " + String(humedad, 0) + " %");

  // Alerta crítica: se registra solo al cruzar el umbral
  bool critica = enZonaCritica();
  if (critica != alertaCriticaActiva) {
    alertaCriticaActiva = critica;
    if (critica) auditar("alertas", "ALERTA_CRITICA", "Alerta crítica en el ESP32: Temp " + String(temperatura, 1) + " °C · Humedad " + String(humedad, 0) + " %");
    else         auditar("alertas", "ALERTA_NORMALIZADA", "Valores de vuelta a rango normal");
  }

  if (!u.automatizacion) return;

  bool deseado[N_ACT];
  deseado[BOMBA]      = temperatura > u.tMax - (estadoAct[BOMBA] ? MARGEN_BOMBA : 0);
  deseado[VENTILADOR] = humedad > u.hMax - (estadoAct[VENTILADOR] ? MARGEN_VENTILADOR : 0);
  deseado[LED_IND]    = temperatura > u.tMax || temperatura < u.tMin || humedad > u.hMax || humedad < u.hMin;
  deseado[BOCINA]     = critica;

  for (int i = 0; i < N_ACT; i++) {
    if (forzado[i] || manualAct[i] || estadoAct[i] == deseado[i]) continue;
    cambiarActuador(i, deseado[i]);
    String motivo = i == BOMBA      ? "temp " + String(temperatura, 1) + " °C, máx " + String(u.tMax, 1) + " °C"
                  : i == VENTILADOR ? "humedad " + String(humedad, 0) + " %, máx " + String(u.hMax, 0) + " %"
                  : i == LED_IND    ? String("valores fuera del rango normal")
                                    : String("valores en zona crítica");
    logMsg(String(ACT_NOMBRE[i]) + (deseado[i] ? " ON" : " OFF") + " (automático: " + motivo + ")");
    String tipo = String(ACT_ID[i]);
    tipo.toUpperCase();
    auditar("dispositivos", tipo + (deseado[i] ? "_ON" : "_OFF"),
            String(ACT_NOMBRE[i]) + (deseado[i] ? " activado" : " desactivado") + " automáticamente por el ESP32 (" + motivo + ")");
  }
}

// =====================================================================================
//  ENVÍO DE DATOS A FIREBASE
// =====================================================================================
// Sensores + latido en UNA sola petición (3.1.3.10: menos transmisiones = menos energía)
void enviarSensoresYLatido(bool incluirSensores) {
  FirebaseJson j;
  if (incluirSensores) {
    j.set("sensores/temperatura", temperatura);
    j.set("sensores/humedadAire", humedad);
    j.set("sensores/timestamp/.sv", "timestamp");   // 3.1.1.2: hora del servidor
  }
  j.set("estado/online", true);
  j.set("estado/ultimaConexion/.sv", "timestamp");
  j.set("estado/rssi", (int)WiFi.RSSI());
  j.set("estado/pendientes", nPendientes);
  j.set("estado/firmware", FIRMWARE_VERSION);
  if (peticionOk(Firebase.RTDB.updateNode(&fbdo, RUTA, &j)) && incluirSensores) {
    tEnviada = temperatura;
    hEnviada = humedad;
  }
}

// =====================================================================================
//  3.1.1.4  ALMACENAMIENTO TEMPORAL EN EL MICROCONTROLADOR (LittleFS) Y SINCRONIZACIÓN
// =====================================================================================
void guardarPendientesEnFlash() {
  File f = LittleFS.open(ARCHIVO_PENDIENTES, "w");
  if (!f) { logMsg("No se pudo escribir el archivo de pendientes"); return; }
  f.write((const uint8_t*)pendientes, nPendientes * sizeof(RegistroPendiente));
  f.close();
}

void cargarPendientesDeFlash() {
  nPendientes = 0;
  File f = LittleFS.open(ARCHIVO_PENDIENTES, "r");
  if (!f) return;
  int corruptos = 0, sinHora = 0;
  RegistroPendiente r;
  while (f.read((uint8_t*)&r, sizeof(r)) == sizeof(r) && nPendientes < MAX_PENDIENTES) {
    if (r.crc != crcRegistro(r)) { corruptos++; continue; }   // 3.1.1.2: se descartan registros dañados
    if (r.epochSeg == 0)         { sinHora++;   continue; }   // de un arranque anterior sin hora: no ubicables
    pendientes[nPendientes++] = r;
  }
  f.close();
  logMsg("Lecturas pendientes recuperadas de la flash: " + String(nPendientes) +
      (corruptos ? " (descartadas por CRC: " + String(corruptos) + ")" : "") +
      (sinHora ? " (sin hora: " + String(sinHora) + ")" : ""));
  if (corruptos || sinHora) guardarPendientesEnFlash();
}

void guardarLecturaLocal() {
  if (nPendientes >= MAX_PENDIENTES) {           // buffer lleno: se descarta la más antigua (24 h)
    memmove(&pendientes[0], &pendientes[1], (MAX_PENDIENTES - 1) * sizeof(RegistroPendiente));
    nPendientes--;
  }
  RegistroPendiente r;
  r.epochSeg = horaValida() ? (uint32_t)time(nullptr) : 0;
  r.millisCaptura = millis();
  r.temperatura = temperatura;
  r.humedad = humedad;
  r.crc = crcRegistro(r);
  pendientes[nPendientes++] = r;

  File f = LittleFS.open(ARCHIVO_PENDIENTES, "a");   // se agrega al final (pocas escrituras en la flash)
  if (f) { f.write((const uint8_t*)&r, sizeof(r)); f.close(); }
  logMsg("Sin conexión: lectura guardada en la flash (" + String(nPendientes) + " pendientes)");
}

// Hora real de un registro (las capturadas antes de tener hora se calculan con millis)
uint64_t horaRegistro(const RegistroPendiente& r) {
  if (r.epochSeg != 0) return (uint64_t)r.epochSeg * 1000ULL;
  if (!horaValida()) return 0;
  return ahoraMs() - (uint64_t)(millis() - r.millisCaptura);
}

// Sube un lote de lecturas pendientes en UNA petición multi-ruta
void sincronizarPendientes() {
  if (nPendientes == 0 || !horaValida()) return;
  int n = min(nPendientes, LOTE_SUBIDA);
  FirebaseJson j;
  int validos = 0;
  for (int i = 0; i < n; i++) {
    const RegistroPendiente& r = pendientes[i];
    if (r.crc != crcRegistro(r)) continue;          // 3.1.1.2: verificación antes de subir
    uint64_t ts = horaRegistro(r);
    if (ts == 0) continue;
    String base = String((unsigned long)((ts / 60000ULL) % MAX_PENDIENTES)) + "/";
    j.set(base + "temperatura", r.temperatura);
    j.set(base + "humedadAire", r.humedad);
    j.set(base + "timestamp", ts);                  // hora REAL de la medición (no la de subida)
    validos++;
  }
  if (validos > 0 && !peticionOk(Firebase.RTDB.updateNode(&fbdo, RUTA + "/lecturas", &j))) return;

  // Confirmado por el servidor: se quitan del buffer y se actualiza la flash
  memmove(&pendientes[0], &pendientes[n], (nPendientes - n) * sizeof(RegistroPendiente));
  nPendientes -= n;
  guardarPendientesEnFlash();
  logMsg("Sincronizadas " + String(validos) + " lecturas guardadas. Quedan " + String(nPendientes));
  if (nPendientes == 0) {
    auditar("dispositivos", "SINCRONIZACION_COMPLETA", "El ESP32 subió todas las lecturas guardadas sin conexión");
    tLatido = 0;   // la app deja de mostrar "subiendo lecturas"
  }
}

// Lectura del minuto: al historial si hay conexión; si no, a la flash
void registrarLecturaDelMinuto() {
  if (!sensorOk) return;
  if (enLinea() && horaValida()) {
    FirebaseJson j;
    j.set("temperatura", temperatura);
    j.set("humedadAire", humedad);
    j.set("timestamp/.sv", "timestamp");
    String slot = String((unsigned long)((ahoraMs() / 60000ULL) % MAX_PENDIENTES));
    if (peticionOk(Firebase.RTDB.setJSON(&fbdo, RUTA + "/lecturas/" + slot, &j))) return;
  }
  guardarLecturaLocal();
}

// =====================================================================================
//  3.1.4.13  RESTAURAR LA ÚLTIMA CONFIGURACIÓN CONOCIDA (funcionar sin conexión desde el arranque)
// =====================================================================================
void cargarConfiguracionLocal() {
  prefs.begin("farma", false);
  if (prefs.getBytesLength("umbrales") == sizeof(Umbrales)) {
    prefs.getBytes("umbrales", &u, sizeof(Umbrales));
    logMsg("Umbrales recuperados de la flash: bomba > " + String(u.tMax, 1) + " °C, ventilador > " + String(u.hMax, 0) + " %");
  }
  ultimoTsUmbrales = prefs.getULong64("ts_umbrales", 0);
  for (int i = 0; i < N_ACT; i++) {
    ultimoTsApp[i] = prefs.getULong64((String("ts_") + ACT_ID[i]).c_str(), 0);
    manualAct[i] = prefs.getBool((String("m_") + ACT_ID[i]).c_str(), false);
    // Por seguridad los equipos arrancan APAGADOS; solo se restaura el estado de los que estaban en manual
    estadoAct[i] = manualAct[i] ? prefs.getBool((String("c_") + ACT_ID[i]).c_str(), false) : false;
  }
}

// =====================================================================================
//  SETUP
// =====================================================================================
void setup() {
  // FALLA SEGURA (fail-safe) #1: relés APAGADOS antes que nada. Ante un corte de luz o reinicio,
  // la bomba y el ventilador nunca arrancan solos al energizar el ESP32.
  digitalWrite(PIN_RELE_BOMBA,      nivelRele(false, BOMBA_ACTIVO_EN_LOW));
  digitalWrite(PIN_RELE_VENTILADOR, nivelRele(false, VENTILADOR_ACTIVO_EN_LOW));
  pinMode(PIN_RELE_BOMBA, OUTPUT);
  pinMode(PIN_RELE_VENTILADOR, OUTPUT);
  aplicarSalidas();
  pinMode(PIN_LED, OUTPUT);
  digitalWrite(PIN_LED, LOW);
  buzzerIniciar();
  tono(0);

  Serial.begin(115200);
  delay(200);
  Serial.println();
  logMsg("==== MONITOREO FARMA · ESP32 " DEVICE_ID " · firmware " FIRMWARE_VERSION " ====");

  // 3.1.3.10 Ahorro de energía
  setCpuFrequencyMhz(80);   // 80 MHz basta para el control y el WiFi (por defecto 240 MHz)
  btStop();                 // Bluetooth apagado: no se usa
  logMsg("CPU a " + String(getCpuFrequencyMhz()) + " MHz, Bluetooth apagado, WiFi en modem-sleep");

  watchdogIniciar();

  if (!LittleFS.begin(true)) logMsg("ERROR: LittleFS no disponible (no habrá almacenamiento local)");
  cargarConfiguracionLocal();
  cargarPendientesDeFlash();
  aplicarSalidas();

  dht.begin();

  // Autodiagnóstico: LED y pitido corto para confirmar que el hardware responde
  digitalWrite(PIN_LED, HIGH);
  tono(2000); delay(150); tono(0);
  digitalWrite(PIN_LED, LOW);

  wifiIniciar();
  firebaseIniciar();
}

// =====================================================================================
//  LOOP (sin delay: todo con temporizadores)
// =====================================================================================
void loop() {
  esp_task_wdt_reset();
  unsigned long ahora = millis();

  wifiVigilar();
  Firebase.ready();          // mantiene y renueva el token de acceso
  vigilarTransiciones();

  // RECONEXIÓN AUTOMÁTICA #4: con WiFi pero sin respuesta del servidor, sondeo cada 10 s
  // para detectar cuándo vuelve Firebase.
  if (wifiConectado && Firebase.ready() && !servidorOk && ahora - ultimoSondeo >= SONDEO_SIN_SERVIDOR_MS) {
    ultimoSondeo = ahora;
    servidorOk = true;       // permite un intento; si falla vuelve a false
    leerComandosYModos();
  }

  if (ahora - tCiclo >= CICLO_CONTROL_MS) {
    tCiclo = ahora;
    bool lecturaNueva = leerSensor();

    if (enLinea()) {
      if (!umbralesLeidos || ahora - tUmbrales >= INTERVALO_UMBRALES_MS) { tUmbrales = ahora; leerUmbrales(); }
      leerComandosYModos();
    }

    controlar();

    if (enLinea()) {
      // 3.1.3.10: los sensores solo se envían si cambiaron lo suficiente o cada 30 s;
      // el latido va cada 10 s. Si toca enviar sensores, el latido viaja en la misma petición.
      bool cambio = lecturaNueva && (isnan(tEnviada) || fabsf(temperatura - tEnviada) >= CAMBIO_MIN_TEMP ||
                                     fabsf(humedad - hEnviada) >= CAMBIO_MIN_HUM);
      bool tocaSensores = sensorOk && (cambio || ahora - tSensores >= INTERVALO_SENSORES_MAX_MS);
      if (tocaSensores || ahora - tLatido >= INTERVALO_LATIDO_MS || tLatido == 0) {
        enviarSensoresYLatido(tocaSensores);
        tLatido = ahora;
        if (tocaSensores) tSensores = ahora;
      }
      sincronizarPendientes();
    }

    if (ahora - tLectura >= INTERVALO_LECTURA_MS) {
      tLectura = ahora;
      registrarLecturaDelMinuto();
    }

    if (lecturaNueva) {
      Serial.printf("[%8lu] T=%.1f°C H=%.0f%% | Bomba:%s%s Vent:%s%s LED:%s Bocina:%s | %s | pendientes:%d\n",
                    millis() / 1000, temperatura, humedad,
                    estadoAct[BOMBA] ? "ON" : "off", manualAct[BOMBA] ? "(M)" : "",
                    estadoAct[VENTILADOR] ? "ON" : "off", manualAct[VENTILADOR] ? "(M)" : "",
                    estadoAct[LED_IND] ? "ON" : "off", estadoAct[BOCINA] ? "ON" : "off",
                    enLinea() ? "EN LINEA" : "SIN CONEXION", nPendientes);
    }
  }

  actualizarIndicadores();
}
