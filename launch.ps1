# Starts the official OpenVoxel launcher with the ModLoader agent injected (Windows PowerShell).
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $root
$jar = Join-Path $root 'dist\ModLoader-v1.0.0-vgame-0.0.8-client.jar'
if (!(Test-Path $jar)) { & cmd /c build.bat; if ($LASTEXITCODE -ne 0) { throw 'Build failed' } }
$home_ = $env:OPENVOXEL_HOME
foreach ($c in @('..\..', '..', '.')) { if (-not $home_ -and (Test-Path (Join-Path $root "$c\OpenVoxelLauncher.exe"))) { $home_ = (Resolve-Path (Join-Path $root $c)).Path } }
if (-not $home_) {
    Write-Host 'EN: OpenVoxelLauncher.exe not found. Set OPENVOXEL_HOME to the launcher folder.'
    Write-Host 'ES: No se encontró OpenVoxelLauncher.exe. Define OPENVOXEL_HOME con la carpeta del launcher.'
    exit 1
}
foreach ($d in 'mods','config','logs','lang','resourcepacks','sounds','music') { New-Item -ItemType Directory -Force (Join-Path $root "instance\$d") | Out-Null }
$env:OPENVOXEL_MODS_DIR = Join-Path $root 'instance'
$env:JAVA_TOOL_OPTIONS = "-javaagent:$jar"
Write-Host '============================================================'
Write-Host ' OpenVoxel ModLoader 1.0.0 (game 0.0.8)'
Write-Host ' EN: In the launcher select version "0.0.8-Debug" and press PLAY.'
Write-Host ' ES: En el launcher selecciona la versión "0.0.8-Debug" y pulsa PLAY.'
Write-Host " Mods: $env:OPENVOXEL_MODS_DIR\mods"
Write-Host '============================================================'
Start-Process -FilePath (Join-Path $home_ 'OpenVoxelLauncher.exe') -WorkingDirectory $home_
