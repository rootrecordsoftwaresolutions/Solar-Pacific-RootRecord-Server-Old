# Copy signed release APK + AAB from a Capacitor android\ tree into Mobile\builds\<dest>.
param(
    [Parameter(Mandatory = $true)][string]$AppDir,
    [Parameter(Mandatory = $true)][string]$DestDir,
    [Parameter(Mandatory = $true)][string]$BaseName,
    [Parameter(Mandatory = $true)][string]$Version,
    [switch]$Native,
    [string]$RequiredUploadSha1 = "",
    [string]$UploadCertFile = ""
)

$ErrorActionPreference = "Stop"
if (Get-Variable -Name PSNativeCommandUseErrorActionPreference -ErrorAction SilentlyContinue) {
    $PSNativeCommandUseErrorActionPreference = $false
}

function Get-CertSha1FromFile([string]$Path) {
    $out = & keytool -printcert -file $Path 2>&1 | Out-String
    if ($out -match 'SHA1:\s*([0-9A-F:]+)') {
        return $Matches[1].ToUpperInvariant()
    }
    throw "Could not read SHA1 from $Path"
}

if (-not $RequiredUploadSha1 -and $UploadCertFile) {
    $certPath = Join-Path $AppDir $UploadCertFile
    if (-not (Test-Path -LiteralPath $certPath)) {
        throw "UploadCertFile not found: $certPath"
    }
    $RequiredUploadSha1 = Get-CertSha1FromFile $certPath
}

function Get-ApksignerPath {
    $sdk = $env:ANDROID_HOME
    if (-not $sdk) { $sdk = $env:ANDROID_SDK_ROOT }
    if (-not $sdk) {
        $lp = Join-Path $AppDir "android\local.properties"
        if (-not (Test-Path -LiteralPath $lp)) { $lp = Join-Path $AppDir "local.properties" }
        if (Test-Path -LiteralPath $lp) {
            foreach ($line in Get-Content -LiteralPath $lp) {
                if ($line -match '^\s*sdk\.dir=(.+)$') {
                    $sdk = $Matches[1].Trim() -replace '\\\\', '\'
                    break
                }
            }
        }
    }
    if (-not $sdk -or -not (Test-Path -LiteralPath $sdk)) { return $null }
    $bt = Get-ChildItem -LiteralPath (Join-Path $sdk "build-tools") -Directory -ErrorAction SilentlyContinue |
        Sort-Object Name -Descending |
        Select-Object -First 1
    if (-not $bt) { return $null }
    $exe = Join-Path $bt.FullName "apksigner.bat"
    if (Test-Path -LiteralPath $exe) { return $exe }
    return $null
}

function Get-AabUploadRsaSha1([string]$Path) {
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $zip = [System.IO.Compression.ZipFile]::OpenRead($Path)
    try {
        $rsaNames = @(
            'META-INF/UPLOAD.RSA',
            'META-INF/CERT.RSA',
            'META-INF/ANDROIDD.RSA'
        )
        foreach ($name in $rsaNames) {
            $entry = $zip.Entries | Where-Object { $_.FullName -eq $name } | Select-Object -First 1
            if ($entry) {
                $sha1 = Get-RsaEntrySha1 $entry
                if ($sha1) { return $sha1 }
            }
        }
        $anyRsa = $zip.Entries | Where-Object { $_.FullName -like 'META-INF/*.RSA' } | Select-Object -First 1
        if ($anyRsa) {
            return Get-RsaEntrySha1 $anyRsa
        }
    } finally {
        $zip.Dispose()
    }
    return $null
}

function Get-RsaEntrySha1($entry) {
    $tmp = [System.IO.Path]::Combine([System.IO.Path]::GetTempPath(), "rr-upload-" + [Guid]::NewGuid().ToString("N") + ".rsa")
    try {
        [System.IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $tmp, $true)
        $out = & keytool -printcert -file $tmp 2>&1 | Out-String
        if ($out -match 'SHA1:\s*([0-9A-F:]+)') { return $Matches[1].ToUpperInvariant() }
    } finally {
        Remove-Item -LiteralPath $tmp -Force -ErrorAction SilentlyContinue
    }
    return $null
}

function Get-ArtifactSha1([string]$Path) {
    $ext = [System.IO.Path]::GetExtension($Path).ToLowerInvariant()
    if ($Path -match '(?i)unsigned') {
        throw "Cannot read upload cert from unsigned artifact: $Path (configure android/keystore.properties + upload-release.jks)."
    }
    if ($ext -eq ".apk") {
        $apksigner = Get-ApksignerPath
        if ($apksigner) {
            $out = & $apksigner verify --print-certs $Path 2>&1 | Out-String
            if ($out -match 'SHA-1 digest:\s*([0-9a-f]+)') {
                $hex = $Matches[1].ToUpperInvariant()
                return ($hex -replace '(..)(?!$)', '$1:')
            }
        }
    }
    if ($ext -eq ".aab") {
        $uploadSha1 = Get-AabUploadRsaSha1 $Path
        if ($uploadSha1) { return $uploadSha1 }
    }
    $out2 = & keytool -printcert -jarfile $Path 2>&1 | Out-String
    if ($out2 -match 'SHA1:\s*([0-9A-F:]+)') {
        return $Matches[1].ToUpperInvariant()
    }
    throw "Could not read signing cert for $Path (install Android SDK build-tools or JDK keytool)."
}

function Assert-UploadCert([string]$Path, [string]$Label) {
    if ($Path -match '(?i)unsigned') {
        Write-Host "WARNING: Skipping cert check for unsigned $Label." -ForegroundColor Yellow
        return
    }
    try {
        $sha1 = Get-ArtifactSha1 $Path
        Write-Host "$Label cert SHA1: $sha1"
        if ($RequiredUploadSha1 -and $sha1 -ne $RequiredUploadSha1) {
            throw "$Label SHA1 $sha1 does not match required upload key $RequiredUploadSha1."
        }
    }
    catch {
        if ($RequiredUploadSha1) { throw }
        Write-Host "WARNING: Could not verify $Label cert ($($_.Exception.Message)); staging for local review." -ForegroundColor Yellow
    }
}

function Select-ReleaseArtifact([string]$Dir, [string]$Ext, [string]$Label) {
    if (-not (Test-Path -LiteralPath $Dir)) {
        throw "No $Label release output dir: $Dir (did gradlew assembleRelease / bundleRelease run?)"
    }
    $candidates = @(
        Get-ChildItem -LiteralPath $Dir -Filter "*.$Ext" -File -ErrorAction SilentlyContinue
        Get-ChildItem -LiteralPath $Dir -Filter "*.$Ext" -File -Recurse -Depth 2 -ErrorAction SilentlyContinue
    ) | Sort-Object FullName -Unique
    $signedCandidates = $candidates | Where-Object {
        $_.Name -notmatch '(?i)debug' -and $_.Name -notmatch '(?i)unsigned'
    }
    if (-not $signedCandidates) {
        $unsignedCandidates = $candidates | Where-Object {
            $_.Name -notmatch '(?i)debug'
        }
        if (-not $unsignedCandidates) {
            throw "No release $Label (*.$Ext) under $Dir. Check the Gradle release build output."
        }
        Write-Host "WARNING: No signed $Label under $Dir; staging unsigned release output for local review." -ForegroundColor Yellow
        return $unsignedCandidates | Sort-Object LastWriteTime -Descending | Select-Object -First 1
    }
    $named = $signedCandidates | Where-Object { $_.Name -match "(?i)app-release\.$Ext`$" }
    if ($named) { return $named | Sort-Object LastWriteTime -Descending | Select-Object -First 1 }
    return $signedCandidates | Sort-Object LastWriteTime -Descending | Select-Object -First 1
}

if ($Native) {
    $apkDir = Join-Path $AppDir "app\build\outputs\apk\release"
    $aabDir = Join-Path $AppDir "app\build\outputs\bundle\release"
} else {
    $apkDir = Join-Path $AppDir "android\app\build\outputs\apk\release"
    $aabDir = Join-Path $AppDir "android\app\build\outputs\bundle\release"
}

$apk = Select-ReleaseArtifact $apkDir "apk" "APK"
$aab = Select-ReleaseArtifact $aabDir "aab" "AAB"

Assert-UploadCert $apk.FullName "APK"
Assert-UploadCert $aab.FullName "AAB"

New-Item -ItemType Directory -Force -Path $DestDir | Out-Null
$apkUnsigned = $apk.Name -match '(?i)unsigned'
$aabUnsigned = $aab.Name -match '(?i)unsigned'
$apkSuffix = if ($apkUnsigned) { "-unsigned" } else { "" }
$aabSuffix = if ($aabUnsigned) { "-unsigned" } else { "" }
$apkDest = Join-Path $DestDir ("{0}-{1}{2}.apk" -f $BaseName, $Version, $apkSuffix)
$aabDest = Join-Path $DestDir ("{0}-{1}{2}.aab" -f $BaseName, $Version, $aabSuffix)

Copy-Item -LiteralPath $apk.FullName -Destination $apkDest -Force
Copy-Item -LiteralPath $aab.FullName -Destination $aabDest -Force

if ($apkUnsigned) {
    Write-Host "WARNING: Staged APK is unsigned and is not Play-ready: $apkDest" -ForegroundColor Yellow
} else {
    Assert-UploadCert $apkDest "Staged APK"
}
if ($aabUnsigned) {
    Write-Host "WARNING: Staged AAB is unsigned and is not Play-ready: $aabDest" -ForegroundColor Yellow
} else {
    Assert-UploadCert $aabDest "Staged AAB"
}

$apkSha256 = (Get-FileHash -LiteralPath $apkDest -Algorithm SHA256).Hash
$aabSha256 = (Get-FileHash -LiteralPath $aabDest -Algorithm SHA256).Hash
$apkSize = (Get-Item -LiteralPath $apkDest).Length
$aabSize = (Get-Item -LiteralPath $aabDest).Length

Write-Output "APK|$apkDest|$($apk.Name)"
Write-Output "AAB|$aabDest|$($aab.Name)"
Write-Host ""
if ($aabUnsigned) {
    Write-Host "Unsigned AAB staged for local review only:" -ForegroundColor Yellow
    Write-Host "  $aabDest"
    Write-Host "  Add release signing before Play upload."
} else {
    Write-Host "Play upload (use this AAB only):" -ForegroundColor Green
    Write-Host "  $aabDest"
}
Write-Host "  Size:   $aabSize bytes"
Write-Host "  SHA256: $aabSha256"
if ($RequiredUploadSha1) {
    Write-Host "  Cert SHA1: $RequiredUploadSha1 (from $UploadCertFile)"
}
