<#
.SYNOPSIS
    Upload a file to a folder in Google Drive.
.PARAMETER FilePath
    Full path to the local file to upload.
.PARAMETER FileName
    Name the file will have in Google Drive.
.PARAMETER FolderName
    Drive folder to upload into (created if absent). Default: Dagboken
.EXAMPLE
    .\.claude\scripts\drive-upload.ps1 -FilePath "app\build\outputs\apk\release\dagboken-2.1.0-20260621-1430.apk" -FileName "dagboken-v2.1.0.apk"
#>
param(
    [Parameter(Mandatory=$true)]  [string]$FilePath,
    [Parameter(Mandatory=$true)]  [string]$FileName,
    [string]$FolderName = "Dagboken"
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

if (-not (Test-Path $FilePath)) {
    Write-Error "File not found: $FilePath"
    exit 1
}

$sizeMb = [math]::Round((Get-Item $FilePath).Length / 1MB, 1)
Write-Host "Uploading $FileName ($sizeMb MB) to Google Drive /$FolderName ..."

# ── Method 1: rclone ───────────────────────────────────────────────────────────
function Try-Rclone {
    if (-not (Get-Command rclone -ErrorAction SilentlyContinue)) { return $false }
    Write-Host "[rclone] Found rclone. Uploading..."
    & rclone copy $FilePath "gdrive:$FolderName/" --progress
    if ($LASTEXITCODE -eq 0) {
        Write-Host "[rclone] Done: gdrive:$FolderName/$FileName"
        return $true
    }
    Write-Warning "[rclone] Upload failed (exit $LASTEXITCODE). Trying next method."
    return $false
}

# ── Method 2: Google Drive for Desktop local sync folder ───────────────────────
function Try-DesktopSync {
    $candidates = @(
        "$env:USERPROFILE\Google Drive\My Drive\$FolderName",
        "$env:USERPROFILE\Google Drive\$FolderName",
        "G:\My Drive\$FolderName",
        "G:\$FolderName"
    )
    foreach ($dest in $candidates) {
        $parent = Split-Path $dest
        if (Test-Path $parent) {
            New-Item -ItemType Directory -Force $dest | Out-Null
            Copy-Item $FilePath (Join-Path $dest $FileName) -Force
            Write-Host "[Drive Desktop] Copied to $dest\$FileName"
            return $true
        }
    }
    return $false
}

# ── Method 3: Google Drive REST API ───────────────────────────────────────────
function Try-RestApi {
    $configPath = Join-Path $env:USERPROFILE ".dagboken-drive-config.json"
    if (-not (Test-Path $configPath)) {
        Write-Host "[REST API] No credentials at $configPath. Skipping."
        return $false
    }

    $cfg = Get-Content $configPath -Raw | ConvertFrom-Json

    # Refresh access token
    Write-Host "[REST API] Refreshing access token..."
    try {
        $tok = Invoke-RestMethod -Method Post `
            -Uri "https://oauth2.googleapis.com/token" `
            -ContentType "application/x-www-form-urlencoded" `
            -Body "client_id=$($cfg.client_id)&client_secret=$($cfg.client_secret)&refresh_token=$($cfg.refresh_token)&grant_type=refresh_token"
        $accessToken = $tok.access_token
    } catch {
        Write-Warning "[REST API] Token refresh failed: $_"
        return $false
    }

    $authHeader = @{ Authorization = "Bearer $accessToken" }

    # Find or create the target folder
    $q = [System.Uri]::EscapeDataString("name='$FolderName' and mimeType='application/vnd.google-apps.folder' and trashed=false")
    $search = Invoke-RestMethod -Uri "https://www.googleapis.com/drive/v3/files?q=$q&fields=files(id,name)" `
        -Headers $authHeader
    if ($search.files.Count -gt 0) {
        $folderId = $search.files[0].id
        Write-Host "[REST API] Found Drive folder '$FolderName' ($folderId)"
    } else {
        Write-Host "[REST API] Creating Drive folder '$FolderName'..."
        $body = "{`"name`":`"$FolderName`",`"mimeType`":`"application/vnd.google-apps.folder`"}"
        $created = Invoke-RestMethod -Method Post `
            -Uri "https://www.googleapis.com/drive/v3/files" `
            -ContentType "application/json" `
            -Body $body `
            -Headers $authHeader
        $folderId = $created.id
    }

    # Multipart upload (metadata + binary)
    Write-Host "[REST API] Uploading file..."
    $boundary = "dagboken_release_$(Get-Date -Format 'yyyyMMddHHmmss')"
    $metaJson  = "{`"name`":`"$FileName`",`"parents`":[`"$folderId`"]}"

    $metaBytes   = [System.Text.Encoding]::UTF8.GetBytes("--$boundary`r`nContent-Type: application/json; charset=UTF-8`r`n`r`n$metaJson`r`n")
    $fileHeader  = [System.Text.Encoding]::UTF8.GetBytes("--$boundary`r`nContent-Type: application/vnd.android.package-archive`r`n`r`n")
    $fileBytes   = [System.IO.File]::ReadAllBytes($FilePath)
    $closing     = [System.Text.Encoding]::UTF8.GetBytes("`r`n--$boundary--")

    $bodyBytes   = New-Object byte[] ($metaBytes.Length + $fileHeader.Length + $fileBytes.Length + $closing.Length)
    [System.Buffer]::BlockCopy($metaBytes,  0, $bodyBytes, 0,                                                          $metaBytes.Length)
    [System.Buffer]::BlockCopy($fileHeader, 0, $bodyBytes, $metaBytes.Length,                                          $fileHeader.Length)
    [System.Buffer]::BlockCopy($fileBytes,  0, $bodyBytes, $metaBytes.Length + $fileHeader.Length,                     $fileBytes.Length)
    [System.Buffer]::BlockCopy($closing,    0, $bodyBytes, $metaBytes.Length + $fileHeader.Length + $fileBytes.Length, $closing.Length)

    try {
        $resp = Invoke-RestMethod -Method Post `
            -Uri "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart" `
            -Headers $authHeader `
            -ContentType "multipart/related; boundary=$boundary" `
            -Body $bodyBytes
        Write-Host "[REST API] Uploaded: $($resp.name) (id $($resp.id))"
        return $true
    } catch {
        Write-Warning "[REST API] Upload failed: $_"
        return $false
    }
}

# ── Run methods in priority order ──────────────────────────────────────────────
if (Try-Rclone)     { exit 0 }
if (Try-DesktopSync){ exit 0 }
if (Try-RestApi)    { exit 0 }

# ── Nothing worked — print setup instructions ──────────────────────────────────
Write-Host ""
Write-Host "─────────────────────────────────────────────────────────────"
Write-Host "  Could not upload automatically. Set up one of these once:"
Write-Host "─────────────────────────────────────────────────────────────"
Write-Host ""
Write-Host "OPTION A  rclone (recommended, easiest)"
Write-Host "  winget install Rclone.Rclone"
Write-Host "  rclone config          # choose 'n New remote', name it 'gdrive', type 'drive'"
Write-Host ""
Write-Host "OPTION B  Google Drive for Desktop"
Write-Host "  https://www.google.com/drive/download/"
Write-Host "  Sign in and let it sync, then re-run the release."
Write-Host ""
Write-Host "OPTION C  REST API (stored credentials)"
Write-Host "  .\.claude\scripts\setup-drive-token.ps1"
Write-Host "  Follow the prompts. Credentials saved to ~/.dagboken-drive-config.json"
Write-Host ""
Write-Host "APK is ready locally at:"
Write-Host "  $FilePath"
Write-Host ""
exit 1
