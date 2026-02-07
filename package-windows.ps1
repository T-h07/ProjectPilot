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

$mvnCmd = Resolve-Maven
$jpackageCmd = Resolve-JPackage

[xml]$pom = Get-Content (Join-Path $root "pom.xml")
$artifactId = $pom.project.artifactId
$version = $pom.project.version

$jarName = "$artifactId-$version.jar"
$jarPath = Join-Path $root ("target\" + $jarName)

if (-not (Test-Path $jarPath)) {
    Write-Host "Building JAR..."
    & $mvnCmd -q -DskipTests package
}

$appDir = Join-Path $root "target\app"
New-Item -ItemType Directory -Force -Path $appDir | Out-Null

Copy-Item $jarPath (Join-Path $appDir $jarName) -Force
& $mvnCmd -q dependency:copy-dependencies -DoutputDirectory=$appDir -DincludeScope=runtime

$iconPath = Join-Path $root "src\main\resources\icons\app.ico"
$destDir = Join-Path $root "dist"

Write-Host "Packaging app image..."
& $jpackageCmd `
  --type app-image `
  --name $artifactId `
  --input $appDir `
  --main-jar $jarName `
  --main-class com.projectpilot.Main `
  --icon $iconPath `
  --dest $destDir `
  --add-modules javafx.controls,javafx.web

Write-Host "Done. App image in: $destDir\$artifactId"
