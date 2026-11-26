@echo off
rem Cierra por completo cualquier AppControl que siga en segundo plano (para probar instalar y desinstalar).
rem OJO: mientras esta cerrado NO se aplican las reglas de bloqueo.
taskkill /F /IM AppControl.exe >nul 2>&1
echo AppControl cerrado. Ya puedes instalar o desinstalar.
pause
