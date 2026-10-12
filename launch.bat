@echo off
rem Starts the official OpenVoxel launcher with the ModLoader agent injected.
rem Game location: OPENVOXEL_HOME, or the folder that contains this repo (..\.. or ..).
setlocal
cd /d "%~dp0"
set "JAR=%~dp0dist\ModLoader-v1.0.0-vgame-0.0.8-client.jar"
if not exist "%JAR%" call build.bat
if not exist "%JAR%" ( echo Build failed. & pause & exit /b 1 )
set "HOME_DIR=%OPENVOXEL_HOME%"
if "%HOME_DIR%"=="" if exist "..\..\OpenVoxelLauncher.exe" set "HOME_DIR=%~dp0..\.."
if "%HOME_DIR%"=="" if exist "..\OpenVoxelLauncher.exe" set "HOME_DIR=%~dp0.."
if "%HOME_DIR%"=="" if exist "%~dp0OpenVoxelLauncher.exe" set "HOME_DIR=%~dp0."
if "%HOME_DIR%"=="" (
  echo OpenVoxelLauncher.exe not found. Set OPENVOXEL_HOME to the launcher folder.
  echo No se encontro OpenVoxelLauncher.exe. Define OPENVOXEL_HOME con la carpeta del launcher.
  pause
  exit /b 1
)
for %%d in (mods config logs lang resourcepacks sounds music) do if not exist "instance\%%d" mkdir "instance\%%d"
set "OPENVOXEL_MODS_DIR=%~dp0instance"
set "JAVA_TOOL_OPTIONS=-javaagent:%JAR%"
echo ============================================================
echo  OpenVoxel ModLoader 1.0.0 (game 0.0.8)
echo  EN: In the launcher select version "0.0.8-Debug" and press PLAY.
echo  ES: En el launcher selecciona la version "0.0.8-Debug" y pulsa PLAY.
echo  Mods: instance\mods   Settings: instance\config
echo ============================================================
start "OpenVoxel + ModLoader" /d "%HOME_DIR%" "%HOME_DIR%\OpenVoxelLauncher.exe"
