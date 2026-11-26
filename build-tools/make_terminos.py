# Genera build-tools/terminos.rtf (la pantalla de términos del instalador) a partir del texto de abajo.
import io

PARAS = [
    ("TÉRMINOS Y CONDICIONES DE AppControl 0.0.1", True),
    ("Lee con calma antes de instalar. Al pulsar «Acepto» confirmas que entiendes lo siguiente.", False),
    ("1. Qué hace este programa", True),
    ("AppControl mide cuánto tiempo se usa cada aplicación y qué sitios web se visitan, y permite bloquear "
     "aplicaciones y sitios con reglas (siempre, por horario o por límite diario).", False),
    ("2. Qué información guarda", True),
    ("Solo el nombre del programa o el dominio del sitio, y la hora. Nunca guarda la dirección completa de una "
     "página, ni los títulos de las páginas, ni su contenido. El historial se conserva 90 días. Todo se guarda en "
     "este equipo, en la carpeta %APPDATA%\\AppControl.", False),
    ("3. Qué cambia en tu equipo", True),
    ("• Se instala solo para tu usuario de Windows, sin pedir permisos de administrador, y puede crear accesos "
     "directos.", False),
    ("• Para bloquear aplicaciones, cierra los programas que tengan una regla activa. Nunca cierra procesos "
     "protegidos del sistema.", False),
    ("• Para bloquear sitios web, abre un servidor local (127.0.0.1, puerto 8081) y configura el proxy de tu "
     "usuario de Windows. Solo ve el dominio. Al cerrar restaura tu configuración anterior y no cambia un proxy "
     "que ya tengas.", False),
    ("• Sigue funcionando en segundo plano, con un icono junto al reloj, para que las reglas se apliquen aunque "
     "cierres la ventana. No se inicia solo con Windows.", False),
    ("4. Cuenta y contraseña", True),
    ("Debes crear una cuenta local. Toda acción sobre las reglas pide esa contraseña. Guarda el código de "
     "recuperación que se muestra una sola vez: sin él no se puede recuperar la cuenta.", False),
    ("5. Grupos y nube (opcional)", True),
    ("Sin internet todo funciona normal. Si creas o te unes a un grupo, se usa la nube de AppControl (Azure) solo "
     "para ese fin. Nadie puede controlar tu equipo sin que tú lo aceptes en tu propio equipo. Quien te controla "
     "puede poner reglas, ver el uso de aplicaciones, los sitios (solo dominio) y el historial, y puede mirar tu "
     "pantalla en vivo (miniaturas mientras tenga abierto el grupo, y en grande si abre tu equipo) y usar tu mouse y tu teclado. Eso queda aceptado en el mismo momento en que aceptas el control, y no se te vuelve a preguntar. Mientras ocurre verás un aviso con un botón «Detener» para cortar el mouse y teclado cuando quieras. Las imágenes y los gestos no se guardan.", False),
    ("6. Desinstalar", True),
    ("Puedes desinstalar desde Configuración de Windows > Aplicaciones. Tus datos quedan en %APPDATA%\\AppControl "
     "hasta que borres esa carpeta.", False),
    ("7. Avisos", True),
    ("Versión de prueba, sin firma digital: Windows puede mostrar un aviso de SmartScreen. El programa se entrega "
     "tal cual, sin garantía. Úsalo solo en equipos propios o con permiso de su dueño.", False),
]


def esc(text):
    out = []
    for ch in text:
        if ch in "\\{}":
            out.append("\\" + ch)
        elif ord(ch) > 127:
            try:
                out.append("\\'%02x" % ord(ch.encode("cp1252")))
            except UnicodeEncodeError:
                out.append("?")
        else:
            out.append(ch)
    return "".join(out)


body = ""
for text, bold in PARAS:
    body += "{\\pard\\sa160 " + ("\\b " if bold else "") + esc(text) + ("\\b0" if bold else "") + "\\par}\n"
rtf = "{\\rtf1\\ansi\\ansicpg1252\\deff0{\\fonttbl{\\f0 Segoe UI;}}\\fs20\n" + body + "}"
with io.open("build-tools/terminos.rtf", "w", encoding="ascii", newline="") as out:
    out.write(rtf)
print("ok", len(rtf))
