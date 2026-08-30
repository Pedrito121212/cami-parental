@echo off
echo ========================================================
echo    Compilador Automatico de APK - Cami Parental 🌸
echo ========================================================
echo.

cd /d "%~dp0"

echo Compilando APK con Gradle...
call gradlew.bat assembleDebug

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [!] Si no tienes Gradle en linea de comandos, abre esta carpeta en Android Studio
    echo     y ve a: Build > Build Bundle(s) / APK(s) > Build APK(s)
    pause
    exit /b
)

echo.
echo Copiando APK compilada a la carpeta del backend...
copy /y "app\build\outputs\apk\debug\app-debug.apk" "..\backend\cami.apk"

echo.
echo ========================================================
echo [OK] APK lista en: backend\cami.apk
echo Sube este archivo 'cami.apk' a tu hosting en /cami/
echo ========================================================
pause
