@echo off
rem Plain-javac build (no Gradle needed). Equivalent to: gradlew.bat jar
setlocal
cd /d "%~dp0"
set "OUT=dist\ModLoader-v1.0.0-vgame-0.8.0-client.jar"
set "GAME=%OPENVOXEL_GAME_JAR%"
if "%GAME%"=="" if exist "..\..\..\Game\versions\0.0.8-Debug\openvoxel-0.0.8-Debug.jar" set "GAME=..\..\..\Game\versions\0.0.8-Debug\openvoxel-0.0.8-Debug.jar"
if "%GAME%"=="" if exist "..\..\Game\versions\0.0.8-Debug\openvoxel-0.0.8-Debug.jar" set "GAME=..\..\Game\versions\0.0.8-Debug\openvoxel-0.0.8-Debug.jar"
if "%GAME%"=="" if exist "game\openvoxel.jar" set "GAME=game\openvoxel.jar"
if "%GAME%"=="" (
  echo Game JAR not found. Set OPENVOXEL_GAME_JAR to openvoxel-0.0.8-Debug.jar ^(download it once with the official launcher^).
  exit /b 1
)
if exist build\classes rmdir /s /q build\classes
mkdir build\classes
if not exist dist mkdir dist
dir /s /b src\main\java\*.java > build\sources.txt
javac --release 11 -encoding UTF-8 -Xlint:-options -classpath "%GAME%;lib\asm-9.7.1.jar;lib\asm-tree-9.7.1.jar" -d build\classes @build\sources.txt
if errorlevel 1 exit /b 1
del build\sources.txt
xcopy /e /i /y /q src\main\resources build\classes >nul
pushd build\classes
jar xf ..\..\lib\asm-9.7.1.jar org
jar xf ..\..\lib\asm-tree-9.7.1.jar org
popd
jar --create --file "%OUT%" --manifest manifest.mf -C build\classes .
if errorlevel 1 exit /b 1
echo Built %OUT%
