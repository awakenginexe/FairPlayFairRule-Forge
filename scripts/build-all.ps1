[CmdletBinding()]
param(
    [string]$OutputDirectory = (Join-Path $PSScriptRoot '..\releases')
)

$projectRoot = Split-Path -Parent $PSScriptRoot
$gradleWrapper = Join-Path $projectRoot 'gradlew.bat'
$targets = @('1.18.2', '1.19.2', '1.19.4', '1.20.1', '1.20.2', '1.20.4', '1.20.6', '1.21.1')

New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null

foreach ($target in $targets) {
    Write-Host "Building FairPlayFairRule for Minecraft $target..."
    & $gradleWrapper clean build --no-daemon "-Ptarget=$target"
    if ($LASTEXITCODE -ne 0) {
        throw "Build failed for Minecraft $target."
    }

    $jar = Get-ChildItem -Path (Join-Path $projectRoot 'build\libs') -Filter "fairplayfairrule-forge-$target-*.jar" |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
    if ($null -eq $jar) {
        throw "Expected a target-labelled JAR for Minecraft $target, but none was produced."
    }

    Copy-Item -LiteralPath $jar.FullName -Destination (Join-Path $OutputDirectory $jar.Name) -Force
}

Write-Host "All target JARs are in $OutputDirectory"
