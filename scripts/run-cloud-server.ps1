$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$projectRoot = Resolve-Path (Join-Path $root "..")
Set-Location $projectRoot

function Resolve-Maven {
    $cmd = Get-Command mvn -ErrorAction SilentlyContinue
    if ($cmd -and $cmd.Source) { return $cmd.Source }

    $toolsDir = Join-Path $projectRoot ".tools"
    $version = "3.9.9"
    $mavenDir = Join-Path $toolsDir ("apache-maven-" + $version)
    $mvnCmd = Join-Path $mavenDir "bin\mvn.cmd"
    if (Test-Path $mvnCmd) { return $mvnCmd }

    throw "Maven not found. Install Maven or run package-windows.ps1 once to download it."
}

$mvnCmd = Resolve-Maven

Write-Host "Building server..."
& $mvnCmd -q -DskipTests package
if ($LASTEXITCODE -ne 0) {
    throw "Maven build failed with exit code $LASTEXITCODE"
}

Write-Host "Copying dependencies..."
& $mvnCmd -q dependency:copy-dependencies -DoutputDirectory=target\server\lib -DincludeScope=runtime
if ($LASTEXITCODE -ne 0) {
    throw "Maven dependency copy failed with exit code $LASTEXITCODE"
}

$libDir = Join-Path $projectRoot "target\server\lib"
$libCheck = Get-ChildItem -Path $libDir -Filter "*.jar" -ErrorAction SilentlyContinue | Select-Object -First 1
if (-not $libCheck) {
    throw "No dependency jars were copied to $libDir. Check Maven output."
}

$jar = Get-ChildItem -Path "target" -Filter "ProjectPilot-*.jar" | Select-Object -First 1
if (-not $jar) { throw "Build failed: no jar found in target/" }

$cp = "$($jar.FullName);$projectRoot\target\server\lib\*"
Write-Host "Starting ProjectPilot Cloud Server..."
& java -cp $cp com.projectpilot.server.CloudServerMain
