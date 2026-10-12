# Builds the ready-to-use client bundle: dist\ModLoader-Client-v1.0.0-vgame-0.0.8.zip
# Contents: dist\<loader jar>, launch.bat/.ps1/.sh, instance\ skeleton, README, LICENSE.
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $root
$jar = 'dist\ModLoader-v1.0.0-vgame-0.0.8-client.jar'
if (!(Test-Path $jar)) { & cmd /c build.bat; if ($LASTEXITCODE -ne 0) { throw 'Build failed' } }
$stage = Join-Path $env:TEMP 'ModLoader-Client-bundle'
if (Test-Path $stage) { Remove-Item -Recurse -Force $stage }
$bundle = Join-Path $stage 'ModLoader-Client'
New-Item -ItemType Directory -Force (Join-Path $bundle 'dist') | Out-Null
Copy-Item $jar (Join-Path $bundle 'dist')
foreach ($f in 'launch.bat','launch.ps1','launch.sh','README.md','LICENSE') { Copy-Item $f $bundle }
foreach ($d in 'mods','config','lang','resourcepacks','sounds','music','logs') { New-Item -ItemType Directory -Force (Join-Path $bundle "instance\$d") | Out-Null }
Copy-Item 'instance\README.md' (Join-Path $bundle 'instance')
Copy-Item 'instance\sounds\README.md' (Join-Path $bundle 'instance\sounds')
Copy-Item 'instance\music\README.md' (Join-Path $bundle 'instance\music')
$zip = Join-Path $root 'dist\ModLoader-Client-v1.0.0-vgame-0.0.8.zip'
if (Test-Path $zip) { Remove-Item $zip }
Compress-Archive -Path $bundle -DestinationPath $zip
Remove-Item -Recurse -Force $stage
Write-Host "Built $zip"
