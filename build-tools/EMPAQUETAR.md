# Cómo generar los instaladores de AppControl

`jpackage` (JDK 21 en `C:\Program Files\Java\jdk-21\bin\jpackage.exe`) **no puede generar para otro sistema**: el paquete de Windows
se arma en Windows, el de Linux en Linux y el de Mac en un Mac. Todos parten del mismo jar.

## 0. Compilar el jar

```
mvn -DskipTests package
mkdir target\dist-input ; copy target\appcontrol-0.0.1-SNAPSHOT.jar target\dist-input\appcontrol.jar
```

## 1. Windows — instalador `.exe` único (el que se pasa a los compañeros)

Resultado: `dist\installer\AppControl-0.0.1.exe` (≈ 92 MB, incluye Java). Asistente en español: bienvenida, **términos y condiciones**
(`build-tools\terminos.rtf`), carpeta de instalación, **accesos directos (opcional)**, progreso y finalizar. Per-user: **no pide administrador**.
Sin firma digital: SmartScreen puede avisar. Se desinstala desde Configuración de Windows > Aplicaciones.

PowerShell (WiX 3.14 portable en el PATH; el argumento de `-D` va entrecomillado):

```
$env:Path = "D:\AppControl\build-tools\wix314\bin;" + $env:Path
$mods='java.base,java.desktop,java.net.http,java.sql,java.naming,java.management,java.xml,java.logging,java.instrument,java.prefs,java.security.jgss,java.security.sasl,jdk.unsupported,jdk.crypto.ec,jdk.zipfs,java.compiler,java.rmi,java.scripting,java.transaction.xa,jdk.management,java.management.rmi,jdk.net'
& "C:\Program Files\Java\jdk-21\bin\jpackage.exe" --type exe --name AppControl --app-version 0.0.1 --vendor AppControl `
  --description "Control de aplicaciones y tiempo" --input target\dist-input --main-jar appcontrol.jar `
  --icon build-tools\appcontrol.ico --license-file build-tools\terminos.rtf --dest dist\installer `
  --win-menu --win-shortcut --win-shortcut-prompt --win-dir-chooser --win-per-user-install --win-menu-group AppControl `
  --add-modules $mods --jlink-options "--strip-debug --no-man-pages --no-header-files" --java-options '"-Dfile.encoding=UTF-8"'
```

- **No quitar `--jlink-options`**: sin él jpackage borra `java.exe`/`javaw.exe` del Java incluido y la actualización
  automática falla (`CreateProcess error=2`).
- Los términos están en `build-tools\make_terminos.py` (texto) → `python -I build-tools\make_terminos.py` regenera `terminos.rtf`.
- WiX 3.14 sin administrador: `wix314-binaries.zip` (github.com/wixtoolset/wix3, release wix3141rtm, SHA256 6AC824E1642D6F7277D0ED7EA09411A508F6116BA6FAE0AA5F2C7DAA2FF43D31) extraído en `build-tools\wix314\bin`.
- Antes de reinstalar, cerrar AppControl del todo (icono junto al reloj > Salir, o `build-tools\cerrar-appcontrol.bat`, que también está en `dist\installer`).
  Una copia vieja en segundo plano hace que la nueva solo "traiga al frente" la vieja. Ajustes > Acerca de muestra la fecha de compilación para comprobarlo.
- Opción de pruebas en el código: `SettingsPageJtMa.CLOSE_EXITS_TEST_DEFAULT_MA` (`false` = cerrar la ventana deja AppControl en segundo plano; `true` = sale del todo).

## Publicar el instalador en GitHub (rama `instalador`)

La rama `instalador` guarda solo el `.exe` y su `README.md` (el código está en `main`). Se trabaja en una carpeta aparte
(`dist\rama-instalador`, ya creada con `git worktree`; en otra PC:
`git worktree add dist/rama-instalador instalador`):

1. Copiar el instalador nuevo a `dist\rama-instalador\AppControl-0.0.1.exe`.
2. Actualizar en su `README.md` la fecha, el tamaño y la huella (`Get-FileHash`).
3. `git -C dist\rama-instalador add .` , `git -C dist\rama-instalador commit -m "Instalador nuevo"` y
   `git -C dist\rama-instalador push`.

GitHub no admite archivos de más de 100 MB (hoy pesa 92,6 MB). Si lo supera, usar «Releases» o Git LFS.

## 2. Windows — carpeta portátil (solo si hace falta)

Mismo comando con `--type app-image` (sin `--win-*` ni `--license-file`) → `dist\AppControl\`. No sacar el `.exe` de su carpeta (necesita `app` y `runtime`).
Windows/antivirus puede borrar archivos al descomprimir un `.zip` sin firma: por eso se prefiere el instalador.

## 3. Linux (en Linux, JDK 17+ y `fakeroot`/`dpkg` para .deb)

Mismo comando con `--type deb` (o `rpm`, o `app-image`), `--icon` con un `.png` (`src/main/resources/images/logo-mark.png`) y rutas con `/`.

## 4. Mac (en un Mac)

`--type dmg` (o `app-image`), `--icon` con un `.icns`. Sin firma de Apple: «clic derecho > Abrir» la primera vez.

## Qué funciona en cada sistema

| Función | Windows | Linux / Mac |
|---|---|---|
| Cuenta, grupos, solicitudes, ver el equipo de otro, pantalla en vivo | Sí | Sí (interfaz y nube) |
| Medir el uso de aplicaciones (ventana activa) | Sí | **No** |
| Bloquear aplicaciones y sitios | Sí | **No** (usa `taskkill`, registro y proxy de Windows) |

## Publicar el servidor de la nube

Ver `overview/work/nube-azure.md` (sección "Publicar una versión nueva"). El servidor y el instalador se publican juntos: la app nueva
espera endpoints que el servidor viejo no tiene.

## Actualizar sin reinstalar (a partir del instalador del 25/11 10:38)

La app instalada busca sola una versión más nueva (25 s después de abrir y cada 4 h; también en Configuración > Actualizaciones) y se actualiza
sin desinstalar: descarga el jar, comprueba tamaño, huella SHA-256 y **firma RSA** (llave pública dentro de la app: `src/main/resources/update-public.b64`),
y un ayudante (`com.appcontrol.update.UpdaterMa`) cambia `app/appcontrol.jar` cuando AppControl se cierra y lo vuelve a abrir. No usa PowerShell ni scripts.

Para sacar una versión nueva (el instalador `.exe` solo hace falta la primera vez en cada PC):
1. `mvn -q -DskipTests package` (en `D:\AppControl`). **No correr `mvn test` entre este paso y el 4**: cambiaría el número de compilación.
2. `python -I build-tools/preparar_actualizacion.py` — calcula la huella, firma con la llave privada (`%APPDATA%\AppControl\secrets\update-private.pem`,
   fuera del proyecto: no perderla) y deja `appcontrol.jar` + `latest.json` en `server/src/main/resources/updates/`.
3. (Opcional) instalador: copiar `target/appcontrol-0.0.1-SNAPSHOT.jar` a `target/dist-input/appcontrol.jar` y correr `jpackage` (sección 1).
4. `cd server; mvn -q -DskipTests package` y publicar el servidor en Azure (ver `overview/work/nube-azure.md`). El servidor sirve `/api/app/latest` y `/api/app/download`.

Las copias instaladas ofrecen "Actualización disponible" al comparar su número de compilación (`build.properties`, 14 dígitos UTC) con el del servidor.
Solo se actualizan las copias instaladas con el instalador (junto a `AppControl.exe`); desde el código (`mvn spring-boot:run`) no se ofrece.
