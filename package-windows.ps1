$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $root

function Resolve-Maven {
    $cmd = Get-Command mvn -ErrorAction SilentlyContinue
    if ($cmd -and $cmd.Source) { return $cmd.Source }

    $toolsDir = Join-Path $root ".tools"
    $version = "3.9.9"
    $mavenDir = Join-Path $toolsDir ("apache-maven-" + $version)
    $mvnCmd = Join-Path $mavenDir "bin\mvn.cmd"
    if (Test-Path $mvnCmd) { return $mvnCmd }

    New-Item -ItemType Directory -Force -Path $toolsDir | Out-Null
    $zipPath = Join-Path $toolsDir ("apache-maven-" + $version + "-bin.zip")
    $urls = @(
        "https://dlcdn.apache.org/maven/maven-3/$version/binaries/apache-maven-$version-bin.zip",
        "https://archive.apache.org/dist/maven/maven-3/$version/binaries/apache-maven-$version-bin.zip",
        "https://archive.apache.org/dist/maven/maven-3/3.9.6/binaries/apache-maven-3.9.6-bin.zip"
    )

    Write-Host "Maven not found. Downloading Maven..."
    $downloaded = $false
    foreach ($u in $urls) {
        try {
            Invoke-WebRequest -Uri $u -OutFile $zipPath
            $downloaded = $true
            break
        } catch {
            $downloaded = $false
        }
    }
    if (-not $downloaded) {
        throw "Failed to download Maven. Please install Maven and retry."
    }
    Expand-Archive -Path $zipPath -DestinationPath $toolsDir -Force
    Remove-Item $zipPath -Force

    if (-not (Test-Path $mvnCmd)) {
        throw "Maven download failed. Please install Maven and retry."
    }
    return $mvnCmd
}

function Resolve-JPackage {
    $cmd = Get-Command jpackage -ErrorAction SilentlyContinue
    if ($cmd -and $cmd.Source) { return $cmd.Source }

    if ($env:JAVA_HOME) {
        $candidate = Join-Path $env:JAVA_HOME "bin\jpackage.exe"
        if (Test-Path $candidate) { return $candidate }
    }

    throw "jpackage not found. Install a JDK (not JRE) and ensure JAVA_HOME or PATH is set."
}

function Resolve-JavaFxJmods {
    param(
        [string]$fxVersion
    )

    if (-not $fxVersion -or $fxVersion.Trim() -eq "") {
        throw "javafx.version not found in pom.xml"
    }

    $toolsDir = Join-Path $root ".tools"
    $fxDir = Join-Path $toolsDir ("javafx-jmods-" + $fxVersion)
    if (Test-Path $fxDir) {
        $existing = Get-ChildItem -Path $fxDir -Recurse -Filter "javafx.base.jmod" -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($existing) { return $existing.DirectoryName }
    }

    New-Item -ItemType Directory -Force -Path $toolsDir | Out-Null
    $zipPath = Join-Path $toolsDir ("openjfx-" + $fxVersion + "-jmods.zip")
    $url = "https://download2.gluonhq.com/openjfx/$fxVersion/openjfx-$fxVersion`_windows-x64_bin-jmods.zip"

    Write-Host "Downloading JavaFX JMODs $fxVersion..."
    Invoke-WebRequest -Uri $url -OutFile $zipPath
    Expand-Archive -Path $zipPath -DestinationPath $fxDir -Force
    Remove-Item $zipPath -Force

    $jmod = Get-ChildItem -Path $fxDir -Recurse -Filter "javafx.base.jmod" -ErrorAction SilentlyContinue | Select-Object -First 1
    if (-not $jmod) {
        throw "JavaFX JMODs download failed."
    }
    return $jmod.DirectoryName
}

$mvnCmd = Resolve-Maven
$jpackageCmd = Resolve-JPackage

$pomPath = Join-Path $root "pom.xml"
[xml]$pom = Get-Content $pomPath
$artifactId = $pom.project.artifactId
$version = $pom.project.version
$javafxVersion = $pom.project.properties.'javafx.version'

$jarName = "$artifactId-$version.jar"
$jarPath = Join-Path $root ("target\" + $jarName)

Write-Host "Building JAR..."
& $mvnCmd -q -f $pomPath -DskipTests package
if ($LASTEXITCODE -ne 0) {
    throw "Maven build failed with exit code $LASTEXITCODE"
}

$appDir = Join-Path $root "target\app"
if (Test-Path $appDir) {
    try {
        Remove-Item -Recurse -Force $appDir
    } catch {
        $timestamp = Get-Date -Format "yyyyMMddHHmmss"
        $appDir = Join-Path $root ("target\app_build_" + $timestamp)
        Write-Host "Warning: Failed to remove target\\app. Using $appDir"
    }
}
New-Item -ItemType Directory -Force -Path $appDir | Out-Null
$libDir = Join-Path $appDir "lib"
New-Item -ItemType Directory -Force -Path $libDir | Out-Null

Copy-Item $jarPath (Join-Path $appDir $jarName) -Force
Write-Host "Copying dependencies to: $libDir"
$copyArgs = @(
    "-f", $pomPath,
    "dependency:copy-dependencies",
    "-DoutputDirectory=$libDir",
    "-DincludeScope=runtime",
    "-DoverWriteReleases=true",
    "-DoverWriteSnapshots=true",
    "-DoverWriteIfNewer=true"
)
& $mvnCmd @copyArgs
if ($LASTEXITCODE -ne 0) {
    throw "Maven dependency copy failed with exit code $LASTEXITCODE"
}
$libCheck = Get-ChildItem -Path $libDir -Filter "*.jar" -ErrorAction SilentlyContinue | Select-Object -First 1
if (-not $libCheck) {
    $defaultDepDir = Join-Path $root "target\dependency"
    if (Test-Path $defaultDepDir) {
        Copy-Item -Path (Join-Path $defaultDepDir "*") -Destination $libDir -Force
        $libCheck = Get-ChildItem -Path $libDir -Filter "*.jar" -ErrorAction SilentlyContinue | Select-Object -First 1
    }
    if (-not $libCheck) {
        throw "No dependency jars were copied to $libDir. Check Maven output."
    }
}

$iconPath = Join-Path $root "src\main\resources\icons\app.ico"
$destDir = Join-Path $root "dist"
$javafxJmods = Resolve-JavaFxJmods $javafxVersion
$appOut = Join-Path $destDir $artifactId

if (Test-Path $appOut) {
    Write-Host "Removing existing app image: $appOut"
    Remove-Item -Recurse -Force $appOut
}

Write-Host "Packaging app image..."
& $jpackageCmd `
  --type app-image `
  --name $artifactId `
  --input $appDir `
  --main-jar $jarName `
  --main-class com.projectpilot.Main `
  --icon $iconPath `
  --dest $destDir `
  --module-path $javafxJmods `
  --add-modules javafx.controls,javafx.web,java.sql,java.sql.rowset,java.net.http,jdk.httpserver

if ($LASTEXITCODE -ne 0) {
    throw "jpackage failed with exit code $LASTEXITCODE"
}

$appLib = Join-Path $appOut "app\lib"
if (Test-Path $libDir) {
    New-Item -ItemType Directory -Force -Path $appLib | Out-Null
    Copy-Item -Path (Join-Path $libDir "*") -Destination $appLib -Force
}

$cfgPath = Join-Path $appOut ("app\" + $artifactId + ".cfg")
if (Test-Path $cfgPath) {
    $classpathLine = "app.classpath=`$APPDIR\$jarName;`$APPDIR\lib\*"
    $cfg = Get-Content $cfgPath
    $out = New-Object System.Collections.Generic.List[string]
    $addedClasspath = $false
    foreach ($line in $cfg) {
        if ($line -match '^app\.classpath=') { continue }
        $out.Add($line)
        if (-not $addedClasspath -and $line -match '^\[Application\]') {
            $out.Add($classpathLine)
            $addedClasspath = $true
        }
    }
    if (-not $addedClasspath) {
        $out.Insert(0, "[Application]")
        $out.Insert(1, $classpathLine)
    }
    Set-Content -Path $cfgPath -Value $out -Encoding ASCII
}

Write-Host "Done. App image in: $destDir\$artifactId"
