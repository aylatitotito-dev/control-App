# Prepara la actualización de la aplicación para que las copias instaladas se actualicen solas.
#   1. (opcional --compilar) compila la aplicación con Maven;
#   2. toma target/appcontrol-0.0.1-SNAPSHOT.jar (el mismo que va en el instalador), calcula su huella SHA-256 y la firma
#      con la llave privada (que está fuera del proyecto: %APPDATA%\AppControl\secrets\update-private.pem);
#   3. deja el archivo y su ficha dentro del servidor: server/src/main/resources/updates/{appcontrol.jar,latest.json}.
# Después hay que compilar el servidor (cd server; mvn -q -DskipTests package) y publicarlo en Azure.
# Uso:  python -I build-tools/preparar_actualizacion.py [--compilar]
import base64
import hashlib
import json
import os
import shutil
import subprocess
import sys
import tempfile
import zipfile

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
JAR = os.path.join(ROOT, "target", "appcontrol-0.0.1-SNAPSHOT.jar")
OUT_DIR = os.path.join(ROOT, "server", "src", "main", "resources", "updates")
KEY = os.path.join(os.environ.get("APPDATA", os.path.expanduser("~")), "AppControl", "secrets", "update-private.pem")
OPENSSL = r"C:\Program Files\Git\usr\bin\openssl.exe"


def main():
    if "--compilar" in sys.argv:
        subprocess.run(["mvn", "-q", "-DskipTests", "package"], cwd=ROOT, check=True, shell=True)
    if not os.path.isfile(JAR):
        sys.exit("Falta compilar la aplicación (mvn -DskipTests package).")
    if not os.path.isfile(KEY):
        sys.exit("No encuentro la llave privada en " + KEY)
    build = ""
    # el número de compilación se lee del propio jar (así no se confunde con una compilación posterior de las pruebas)
    with zipfile.ZipFile(JAR) as archive:
        props = archive.read("BOOT-INF/classes/build.properties").decode("utf-8")
    for line in props.splitlines():
        if line.startswith("build="):
            build = "".join(ch for ch in line.split("=", 1)[1] if ch.isdigit())
    if not build.isdigit() or len(build) != 14:
        sys.exit("El número de compilación no es válido: " + build)
    with open(JAR, "rb") as handle:
        data = handle.read()
    sha = hashlib.sha256(data).hexdigest()
    with tempfile.TemporaryDirectory() as tmp:
        sig_file = os.path.join(tmp, "firma.bin")
        subprocess.run([OPENSSL, "dgst", "-sha256", "-sign", KEY, "-out", sig_file, JAR], check=True)
        with open(sig_file, "rb") as handle:
            signature = base64.b64encode(handle.read()).decode("ascii")
    os.makedirs(OUT_DIR, exist_ok=True)
    shutil.copyfile(JAR, os.path.join(OUT_DIR, "appcontrol.jar"))
    info = {"available": True, "version": "0.0.1", "build": build, "size": len(data), "sha256": sha, "signature": signature}
    with open(os.path.join(OUT_DIR, "latest.json"), "w", encoding="utf-8") as handle:
        json.dump(info, handle, indent=2)
    print("Actualización lista: compilación", build, "·", round(len(data) / 1048576, 1), "MB · sha256", sha[:16] + "…")


if __name__ == "__main__":
    main()
