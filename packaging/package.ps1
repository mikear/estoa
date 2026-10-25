param(
    [string]$Jar = '',
    [string]$Out = 'dist',
    [switch]$RequireInstaller
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot

if ([string]::IsNullOrWhiteSpace($Jar)) {
    $Jar = Join-Path $root 'src\orion-app\target\estoa.jar'
}
$Jar = (Resolve-Path -LiteralPath $Jar).Path

if (-not [IO.Path]::IsPathRooted($Out)) {
    $Out = Join-Path $root $Out
}
$Out = [IO.Path]::GetFullPath($Out)
if ($Out -eq $root) {
    throw 'La salida no puede ser la raiz del repositorio'
}

$work = Join-Path $root 'build\packaging'
$stage = Join-Path $work 'staging'
$jfx = Join-Path $work 'jfx'
$appImageRoot = Join-Path $work 'app-image'
$versionFile = Join-Path $root 'src\orion-app\target\classes\version.properties'
if (-not (Test-Path -LiteralPath $versionFile)) {
    throw "No existe $versionFile. Compila antes con: mvn package -DskipTests -Pwindows"
}
$version = (Get-Content -LiteralPath $versionFile |
        Where-Object { $_ -match '^version=' } |
        Select-Object -First 1) -replace '^version=', ''
$version = $version.Trim()
if (-not $version) {
    throw 'No se pudo leer la version de version.properties'
}

$jpackage = 'jpackage'
if ($env:JAVA_HOME) {
    $candidate = Join-Path $env:JAVA_HOME 'bin\jpackage.exe'
    if (Test-Path -LiteralPath $candidate) {
        $jpackage = $candidate
    }
}
if (-not (Get-Command $jpackage -ErrorAction SilentlyContinue) -and
        -not (Test-Path -LiteralPath $jpackage)) {
    throw 'jpackage no esta disponible. Define JAVA_HOME con un JDK 21+'
}

$wix = Get-Command candle.exe -ErrorAction SilentlyContinue
if ($RequireInstaller -and -not $wix) {
    throw 'WiX 3 no esta instalado y se solicito -RequireInstaller'
}

Remove-Item -LiteralPath $work -Recurse -Force -ErrorAction SilentlyContinue
Remove-Item -LiteralPath $Out -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $stage, $jfx, $appImageRoot, $Out | Out-Null
Copy-Item -LiteralPath $Jar -Destination (Join-Path $stage 'estoa.jar') -Force

Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [IO.Compression.ZipFile]::OpenRead($Jar)
try {
    $fx = @($zip.Entries | Where-Object { $_.FullName -like 'BOOT-INF/lib/javafx-*-win.jar' })
    if ($fx.Count -eq 0) {
        throw 'El JAR no contiene JavaFX para Windows (falta -Pwindows)'
    }
    foreach ($entry in $fx) {
        [IO.Compression.ZipFileExtensions]::ExtractToFile(
                $entry, (Join-Path $jfx $entry.Name), $true)
    }
} finally {
    $zip.Dispose()
}

$natives = Join-Path $stage 'natives'
New-Item -ItemType Directory -Force -Path $natives | Out-Null
Get-ChildItem -LiteralPath $jfx -Filter '*.jar' | ForEach-Object {
    $inner = [IO.Compression.ZipFile]::OpenRead($_.FullName)
    try {
        $inner.Entries | Where-Object { $_.FullName -match '\.dll$' } | ForEach-Object {
            [IO.Compression.ZipFileExtensions]::ExtractToFile(
                    $_, (Join-Path $natives ([IO.Path]::GetFileName($_.FullName))), $true)
        }
    } finally {
        $inner.Dispose()
    }
}
$dlls = @(Get-ChildItem -LiteralPath $natives -Filter '*.dll').Count
if ($dlls -eq 0) {
    throw 'No se extrajeron DLLs de JavaFX'
}
Write-Host "Nativos JavaFX: $dlls DLL"

$modules = 'java.base,java.desktop,java.logging,java.sql,java.naming,java.management,' +
        'java.instrument,java.net.http,java.xml,jdk.crypto.ec,jdk.unsupported,jdk.zipfs'
$appArgs = @(
    '--type', 'app-image',
    '--name', 'Estoa',
    '--input', $stage,
    '--main-jar', 'estoa.jar',
    '--main-class', 'org.springframework.boot.loader.launch.JarLauncher',
    '--java-options', '-Djava.library.path=$APPDIR/natives',
    '--java-options', '--enable-native-access=ALL-UNNAMED',
    '--add-modules', $modules,
    '--app-version', $version,
    '--vendor', 'Estoa',
    '--dest', $appImageRoot
)
$icon = Join-Path $PSScriptRoot 'estoa.ico'
if (Test-Path -LiteralPath $icon) {
    $appArgs += @('--icon', $icon)
}
& $jpackage @appArgs
if ($LASTEXITCODE -ne 0) {
    throw "jpackage app-image fallo con codigo $LASTEXITCODE"
}

$appImage = Join-Path $appImageRoot 'Estoa'
if (-not (Test-Path -LiteralPath (Join-Path $appImage 'Estoa.exe'))) {
    throw "No se genero la app image en $appImage"
}

$portable = Join-Path $Out 'Estoa-windows-x64-portable.zip'
Compress-Archive -Path $appImage -DestinationPath $portable -Force
Write-Host "Portable: $portable"

if ($wix) {
    $installerArgs = @(
        '--type', 'exe',
        '--name', 'Estoa',
        '--app-image', $appImage,
        '--dest', $Out,
        '--app-version', $version,
        '--vendor', 'Estoa',
        '--win-per-user-install',
        '--win-menu',
        '--win-shortcut',
        '--win-upgrade-uuid', '8a4b6c2e-3d5f-4a7b-9c1d-2e3f4a5b6c7d'
    )
    if (Test-Path -LiteralPath $icon) {
        $installerArgs += @('--icon', $icon)
    }
    & $jpackage @installerArgs
    if ($LASTEXITCODE -ne 0) {
        throw "jpackage exe fallo con codigo $LASTEXITCODE"
    }
    $builtInstaller = Get-ChildItem -LiteralPath $Out -Filter 'Estoa-*.exe' |
            Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if ($builtInstaller) {
        Move-Item -LiteralPath $builtInstaller.FullName `
                -Destination (Join-Path $Out 'Estoa-windows-x64.exe') -Force
        Write-Host "Instalador: $(Join-Path $Out 'Estoa-windows-x64.exe')"
    }
} elseif ($RequireInstaller) {
    throw 'WiX 3 no esta instalado'
} else {
    Write-Warning 'WiX 3 no instalado: se omite el instalador .exe (solo portable)'
}

$checksumFile = Join-Path $Out 'SHA256SUMS-windows.txt'
$lines = Get-ChildItem -LiteralPath $Out -File |
        Where-Object { $_.Extension -in '.exe', '.zip' } |
        Sort-Object Name |
        ForEach-Object {
            $hash = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
            "$hash  $($_.Name)"
        }
Set-Content -LiteralPath $checksumFile -Value $lines -Encoding ASCII
Write-Host "Checksums: $checksumFile"
