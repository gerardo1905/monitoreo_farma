# Plan de pruebas – App Invernadero

**Versión:** 1.0  **Dispositivos de prueba (mínimo 2):** ____________  **Fecha:** ____________

Llenar las columnas *Resultado obtenido* y *Estado* (Aprobada / Falló) y adjuntar capturas como evidencia (rúbrica 2.1.3.11).

| ID | Funcionalidad | Pasos | Resultado esperado | Resultado obtenido | Estado |
|---|---|---|---|---|---|
| AUT-01 | Registro con contraseña válida | Registrar correo nuevo con contraseña que cumple la política | Cuenta creada, ingresa al panel, correo de verificación enviado |  |  |
| AUT-02 | Registro con contraseña débil | Escribir 'abc123' en el registro | El botón no avanza; el checklist muestra los requisitos faltantes |  |  |
| AUT-03 | Login correcto | Ingresar con credenciales válidas | Entra al panel y se registra LOGIN en Registros |  |  |
| AUT-04 | Login incorrecto | Contraseña errónea 1 vez | Mensaje genérico 'Credenciales incorrectas' y registro LOGIN_FALLIDO |  |  |
| AUT-05 | Bloqueo por intentos | 5 contraseñas erróneas seguidas | Cuenta bloqueada, aun con la contraseña correcta no entra; registro CUENTA_BLOQUEADA |  |  |
| AUT-06 | Desbloqueo por recuperación | Con la cuenta bloqueada, usar 'Recuperar contraseña' y definir una nueva | La cuenta vuelve a permitir acceso |  |  |
| AUT-07 | Cambio de contraseña | Perfil > Cambiar contraseña con la actual correcta y una nueva válida | Mensaje de éxito y registro PASSWORD_CAMBIADA |  |  |
| AUT-08 | Cambio con contraseña actual incorrecta | Perfil > Cambiar contraseña con actual errónea | Error 'La contraseña actual es incorrecta'; no cambia nada |  |  |
| AUT-09 | Cierre por inactividad | Dejar la app abierta 10 min sin tocar | Sesión cerrada, vuelve al login, registro LOGOUT_INACTIVIDAD |  |  |
| AUT-10 | Cuenta deshabilitada | Admin desactiva a un usuario con sesión abierta | El usuario es expulsado al login |  |  |
| ROL-01 | Usuario sin Registros ni Ajustes | Entrar como usuario normal | La barra inferior solo muestra Inicio, Gráficas y Perfil; no aparece Panel de administración |  |  |
| ROL-02 | Solo el admin edita ajustes | Entrar como admin > Ajustes | Se pueden editar rangos, automatización y modo de equipos; con usuario la pestaña no existe |  |  |
| ROL-03 | Admin gestiona cuentas | Admin cambia rol y activa/desactiva cuentas | Cambios aplicados y registrados en 'cuentas'; no existe opción de eliminar |  |  |
| DIS-01 | Estado en línea | Dispositivo/simulador activo | Muestra 'en línea' y 'Última señal hace X s' |  |  |
| DIS-02 | Estado desconectado | Cerrar sesión en todos los teléfonos y esperar >15 s | Muestra 'desconectado' |  |  |
| DIS-03 | Encendido manual | Encender Ventilador con el interruptor | Estado cambia a manual, no es revertido por la automatización; registro VENTILADOR_ON |  |  |
| DIS-04 | Volver a automático | Admin: Ajustes > Modo de los equipos, activar el interruptor del equipo | Registro MODO_AUTOMATICO y la automatización retoma el control |  |  |
| DIS-05 | LED y bocina | Valor fuera de rango normal / de alarma | LED se enciende en advertencia; bocina en zona crítica |  |  |
| ALM-01 | Alarma en primer plano | Forzar valor crítico con la app abierta | Pantalla roja de alarma, sonido y vibración |  |  |
| ALM-02 | Alarma en segundo plano | Valor crítico con app minimizada y pantalla bloqueada | Notificación de alarma con sonido; el servicio 'Monitoreo activo' sigue activo |  |  |
| ALM-03 | Silenciar alarma | Pulsar silenciar | Se detiene sonido y queda registro ALARMA_SILENCIADA |  |  |
| ALM-04 | Dato antiguo | Dejar el dispositivo sin enviar datos >60 s | No suena alarma por un dato viejo |  |  |
| GRA-01 | Gráficas | Gráficas > 1 h / 6 h / 24 h tras varios minutos de datos | Curvas de temperatura y humedad con mín/máx/promedio y líneas de umbral |  |  |
| REG-01 | Registros ordenados | Admin > Registros, revisar pestañas | Eventos por categoría, agrupados por día, más recientes primero |  |  |
| REG-02 | Todo deja registro | Realizar cada acción de las pruebas anteriores | Cada acción aparece en su categoría |  |  |
| SEG-01 | Reglas de BD | Con usuario normal intentar leer /registros desde la consola de simulación de reglas | Acceso denegado |  |  |
| SEG-02 | Multi-dispositivo | Abrir la app en 2 teléfonos con sesión | Solo un teléfono simula datos; ambos muestran lo mismo y ambos suenan la alarma |  |  |
| NAV-01 | Barra inferior con iconos | Recorrer Inicio, Gráficas, Registros (admin), Ajustes y Perfil | La barra se mantiene en todas; la pestaña activa se ve oscura con transición suave; usuario normal no ve Registros |  |  |
| AJU-01 | Umbrales numéricos | Ajustes: escribir un valor nuevo en un campo de mínimo/máximo | Aparece 'Guardar cambios'; al guardar se registra UMBRALES_MODIFICADOS |  |  |
| AJU-02 | Validación de umbrales | Escribir un mínimo mayor que el máximo o un valor fuera de rango | Error junto al campo y no se guarda |  |  |
| SIM-01 | Humedad sube sola | Automatización desactivada, esperar varios minutos | La humedad aumenta gradualmente y supera el 50 %; con la automatización activa, al pasar el máximo se enciende el ventilador y la humedad baja |  |  |
| EMG-01 | Alarma con cuenta atrás | Provocar temperatura/humedad crítica ALTA | Pantalla de alarma con cuenta atrás de 2 min y botón 'Activar ventilador ahora' |  |  |
| EMG-02 | Equipo manual de emergencia | Pulsar el botón de activar equipo durante la cuenta atrás | Equipo encendido (registro BOMBA_ON o VENTILADOR_ON); se apaga solo al normalizarse |  |  |
| EMG-03 | Equipo automático | No tocar nada durante 2 min con valor crítico alto | El sistema enciende el equipo; registro EMERGENCIA_AUTOMATICA |  |  |
| EMG-04 | Alerta repetida | Silenciar la alarma y dejar el valor crítico | La alarma vuelve a sonar cada 2 min hasta que se normaliza |  |  |
| EMG-05 | Fin de emergencia | Esperar a que los valores vuelvan al rango normal | Se apaga el equipo, deja de sonar y queda EMERGENCIA_RESUELTA |  |  |

## Pruebas unitarias automáticas

Ejecutar en Android Studio: clic derecho en `app/src/test` > *Run Tests* (`PasswordPolicyTest`: política de contraseñas, formato de correo y clave de bloqueo).
