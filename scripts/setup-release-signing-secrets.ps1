$ErrorActionPreference = 'Stop'
$Repository = 'xiaobei206/kaixinguo-android'
$Root = Split-Path -Parent $PSScriptRoot
$KeyPath = Join-Path $Root 'kaixinguo-release.jks'
$SecretsPath = Join-Path $Root 'signing-secrets.txt'

if (-not (Get-Command gh -ErrorAction SilentlyContinue)) {
    throw 'GitHub CLI (gh) is not installed. Install it from https://cli.github.com/ and run gh auth login first.'
}
if (-not (Test-Path -LiteralPath $KeyPath)) {
    throw "Keystore not found: $KeyPath. Extract the private backup bundle into the repository root first."
}
if (-not (Test-Path -LiteralPath $SecretsPath)) {
    throw "Credential file not found: $SecretsPath. Extract the private backup bundle into the repository root first."
}

& gh auth status --hostname github.com
if ($LASTEXITCODE -ne 0) {
    throw 'GitHub CLI is not authenticated. Run gh auth login and retry.'
}

$Values = ConvertFrom-StringData (Get-Content -LiteralPath $SecretsPath -Raw)
foreach ($Name in @('ANDROID_KEYSTORE_PASSWORD', 'ANDROID_KEY_ALIAS', 'ANDROID_KEY_PASSWORD')) {
    if (-not $Values.ContainsKey($Name) -or [string]::IsNullOrWhiteSpace($Values[$Name])) {
        throw "Missing required field $Name in signing-secrets.txt."
    }
}

function Set-RepositorySecret([string]$Name, [string]$Value) {
    $Value | & gh secret set $Name --repo $Repository
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to set GitHub Actions secret $Name. Check repository write permissions."
    }
    Write-Host "Configured Actions secret: $Name"
}

$Base64Key = [Convert]::ToBase64String([IO.File]::ReadAllBytes($KeyPath))
Set-RepositorySecret 'ANDROID_KEYSTORE_BASE64' $Base64Key
Set-RepositorySecret 'ANDROID_KEYSTORE_PASSWORD' $Values['ANDROID_KEYSTORE_PASSWORD']
Set-RepositorySecret 'ANDROID_KEY_ALIAS' $Values['ANDROID_KEY_ALIAS']
Set-RepositorySecret 'ANDROID_KEY_PASSWORD' $Values['ANDROID_KEY_PASSWORD']
Write-Host 'Release-signing secrets configured. Secret values were not printed.'
Write-Host 'Keep the private backup bundle offline and never commit it to the public repository.'
