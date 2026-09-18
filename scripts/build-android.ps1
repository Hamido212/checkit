$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$keyDirectory = Join-Path $env:LOCALAPPDATA 'CheckitSigning'
New-Item -ItemType Directory -Force $keyDirectory | Out-Null
$keyFile = Join-Path $keyDirectory 'checkit-release.jks'
$passwordFile = Join-Path $keyDirectory 'password.dpapi.xml'
if (-not $env:JAVA_HOME) { $env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr' }
if (-not $env:ANDROID_HOME) { $env:ANDROID_HOME = Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
if (-not (Test-Path -LiteralPath $passwordFile)) {
    if (Test-Path -LiteralPath $keyFile) { throw 'Signing key exists but its protected password is missing.' }
    $generatedPassword = [Convert]::ToBase64String([System.Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
    ConvertTo-SecureString -String $generatedPassword -AsPlainText -Force | Export-Clixml -LiteralPath $passwordFile
    $generatedPassword = $null
}
$securePassword = Import-Clixml -LiteralPath $passwordFile
$env:CHECKIT_KEY_PASSWORD = [System.Net.NetworkCredential]::new('', $securePassword).Password
$env:CHECKIT_KEYSTORE = $keyFile
try {
    if (-not (Test-Path -LiteralPath $keyFile)) {
        & "$env:JAVA_HOME\bin\keytool.exe" -genkeypair -keystore $keyFile -storepass:env CHECKIT_KEY_PASSWORD -keypass:env CHECKIT_KEY_PASSWORD -alias checkit -keyalg RSA -keysize 3072 -validity 10000 -dname 'CN=Checkit, O=Checkit Open Source, C=DE'
        if ($LASTEXITCODE -ne 0) { throw 'Key creation failed' }
    }
    & "$projectRoot\android\gradlew.bat" -p "$projectRoot\android" assembleRelease
    if ($LASTEXITCODE -ne 0) { throw 'Android release build failed' }
} finally {
    Remove-Item Env:CHECKIT_KEY_PASSWORD -ErrorAction SilentlyContinue
    Remove-Item Env:CHECKIT_KEYSTORE -ErrorAction SilentlyContinue
}
