# AppControl: reglas del proyecto

Java 17 + Maven + Spring Boot, app de escritorio (Swing + FlatLaf). Contexto completo en `overview/context/`.

## Autoría del equipo (obligatorio en TODO código nuevo)

Variables locales, parámetros (también los de lambdas), campos privados y auxiliares llevan las iniciales del equipo.
No se aplica a tablas, columnas, endpoints REST ni campos de entidades/DTO (las tablas tienen nombre explícito en `@Table`).

Los sufijos se **alternan por archivo** entre `Jt`, `Ma` y `JtMa` (nunca todo el proyecto igual ni toda una carpeta igual).
Aplica a variables, parámetros, campos privados, **métodos propios y nombres de clase** (clases, interfaces, registros, enums y tipos internos).
Ejemplo: `usageJtMa`, `limitsMa`, `minutesJt`, `evMa ->`, clase `UsageServiceJt`. Las clases de prueba terminan en `Test` DESPUÉS de las iniciales (`UsageServiceMaTest`) para que Maven las ejecute. Un archivo nuevo elige el sufijo distinto al de los archivos vecinos.
Los métodos que se sobrescriben en varias clases (p. ej. `refresh...`) comparten un único nombre.

Nada de variables sueltas sin iniciales (`e`, `p`, `bb`, `i`).

## Seguridad / Windows

- Nunca lanzar PowerShell desde la app (ni con `-EncodedCommand` ni `-ExecutionPolicy Bypass`): Defender lo marca. Usar `reg query` y lectura nativa.
- No esquivar ni pedir desactivar protecciones del sistema.
- Sitios web: guardar solo dominio y fecha, nunca la URL completa ni el contenido.
- Títulos de ventana: solo se leen cuando el programa al frente es un navegador, solo en memoria, solo para deducir el dominio (`SiteTitleIndexJt`); nunca se guardan ni se registran. Hay un interruptor en Ajustes ("Sitio en primer plano", clave `site_focus`) que pide la contraseña para cambiarlo. En cualquier otra aplicación no se lee ningún título.

## Datos

- Perfil `desktop` (por defecto): H2 en archivo. `local`: MySQL de desarrollo. `azure`: MySQL en Azure.
- La app debe funcionar sin internet; Azure solo para grupos/vínculo remoto.

## Reglas de bloqueo

- Una sola tabla `block_rule` (apps y sitios): modos ALWAYS / SCHEDULE / QUOTA y estados ACTIVE / PAUSED / INACTIVE.
- El motor (`RuleEnforcerMa`) solo cierra aplicaciones; nunca procesos protegidos (`RuleServiceJt.PROTECTED_JTMA`) ni a AppControl.
- Cuenta local obligatoria (crear cuenta al abrir por primera vez). TODA acción sobre reglas pide la contraseña de la cuenta cada vez: crear, editar, pausar, activar, desactivar, quitar. También para apagar el bloqueo de sitios o el seguimiento de pestañas, y para salir desde la bandeja.
- Contraseña: PBKDF2-SHA256 con sal; nunca en texto. 5 errores seguidos = espera de 30 s que se duplica (máx. 5 min). Código de recuperación de 16 caracteres, se muestra una sola vez, se guarda solo su huella.
- Las reglas se aplican en segundo plano aunque no haya sesión iniciada.
- Reglas de sitios: se aplican con un proxy local (127.0.0.1:8081) y un script PAC en /proxy.pac, configurados por usuario en el registro (HKCU, sin administrador). Solo se ve el dominio. Hay que restaurar la configuración al cerrar y nunca pisar un proxy/script que ya tenga el usuario.
- Al probar con procesos reales: no cerrar "cualquier copia anterior" de AppControl; puede ser la que el usuario tiene abierta.

## Historial

- Tabla `activity_event`: sesiones de apps, visitas a sitios (agrupadas) y bloqueos. Solo programa o dominio + hora; se conserva 90 días.
- Nunca guardar la URL completa ni títulos de páginas.
- No cerrar ni reiniciar una copia de AppControl que el usuario tenga abierta para probar; si el puerto 8080 está ocupado, verificar con pruebas y avisar.

## Nube y control remoto

- La app habla solo con la API (`server/`, Azure) por HTTPS; nunca con la base. Detalle en `overview/work/nube-azure.md`.
- Control **con consentimiento de los dos lados**: nada se aplica ni se ve hasta que la persona acepta en SU equipo (pide su contraseña). Al aceptar queda aceptado todo
  (reglas, actividad, pantalla en vivo, mouse y teclado); la ventana de solicitud debe decirlo con claridad y los términos del instalador también (`build-tools/make_terminos.py`).
- Pantalla en vivo y gestos de mouse/teclado: **solo en memoria del servidor**, nunca en base de datos ni disco. Siempre hay un aviso visible en la PC controlada y,
  con mouse/teclado, un botón «Detener» sin contraseña. **No agregar ejecución de comandos ni consola remota.**
- El dueño ve el historial (eventos) solo de los tramos de control aceptados (`control_session`), también después de que terminen; nunca lo anterior.
- Las reglas de un grupo no se pueden editar ni quitar desde la PC controlada (solo se muestran como «Bloqueado por X»). No guardar tokens ni secretos en el repo.
- Publicar servidor e instalador **juntos** (ver `build-tools/EMPAQUETAR.md`); pedir confirmación antes de publicar en Azure o gastar crédito. Al terminar el trabajo del usuario: borrar `appcontrol-rg`.
- Pruebas con dos PC o con la copia del usuario abierta: no cerrarla; usar pruebas automáticas y avisar.

## Aspecto (estilo Zoom)

- Colores y esquinas se definen solo en `ThemeManagerMa` (azul intenso de acento, `AppControl.frame/surface/border/hover/selected/muted`); no escribir colores azules fijos: usar `$Component.accentColor` o `ThemeManagerMa.accentColorJt()`.
- Estructura: barra superior + riel de iconos + superficie redondeada (`MainPanelJt`). Configuración > Aspecto elige modo (claro/oscuro/sistema) y color del tema.

## Actualizaciones

- La app se actualiza sola desde el servidor (paquete `update/`). Todo archivo de actualización va **firmado** (RSA) y la app comprueba tamaño, huella y firma antes de instalar. La llave privada nunca va en el proyecto.
- No agregar otra forma de descargar y ejecutar código. El ayudante de actualización usa solo Java (sin PowerShell ni scripts). Pasos para publicar: `build-tools/EMPAQUETAR.md`.
