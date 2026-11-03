# Bygger Op Med Dig og udgiver den på GitHub, så appen opdaterer sig selv.
# Brug: hæv versionCode og versionName i app\build.gradle.kts, og kør så:
#   powershell -ExecutionPolicy Bypass -File release.ps1 -Notes "Hvad er nyt"
param([string]$Notes = "Ny version")

$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

# Java 17 fejler på Windows, hvis temp-mappen ligger under en sti med mellemrum
New-Item -ItemType Directory -Force C:\Android\tmp | Out-Null
$env:TEMP = "C:\Android\tmp"; $env:TMP = "C:\Android\tmp"
$env:JAVA_HOME = (Get-ChildItem "C:\Program Files\Microsoft" -Directory -Filter "jdk-17*" | Select-Object -First 1).FullName
$env:ANDROID_HOME = "C:\Android\Sdk"
$env:JAVA_TOOL_OPTIONS = "-Djdk.net.unixdomain.tmpdir=C:\Android\tmp -Djava.io.tmpdir=C:\Android\tmp"

$version = (Select-String -Path app\build.gradle.kts -Pattern 'versionName = "(.+)"').Matches[0].Groups[1].Value
$tag = "v$version"
$existing = gh release list --json tagName --jq ".[].tagName"
if ($existing -contains $tag) { throw "Release $tag findes allerede. Hæv versionName i app\build.gradle.kts først." }

& C:\Android\gradle-8.7\bin\gradle.bat assembleRelease --no-daemon --console=plain -q
if ($LASTEXITCODE -ne 0) { throw "Bygning fejlede" }
Copy-Item app\build\outputs\apk\release\app-release.apk .\OpMedDig.apk -Force

# Navnet OpMedDig.apk skal være det samme hver gang; appen leder efter præcis det navn
gh release create $tag OpMedDig.apk --title "Op Med Dig $version" --notes $Notes
Write-Host "Udgivet $tag. Telefonerne henter den inden for 12 timer, eller når appen åbnes."
