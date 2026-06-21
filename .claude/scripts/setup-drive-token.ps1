<#
.SYNOPSIS
    One-time OAuth setup for Google Drive uploads.
    Walks through a device-code flow and saves client credentials +
    refresh token to ~/.dagboken-drive-config.json.

.DESCRIPTION
    You need a Google OAuth client (Desktop type) with Drive scope.
    Quickest path:
      1. Open https://console.cloud.google.com/apis/credentials
      2. Create OAuth client ID → Desktop app → Download JSON
      3. Run this script and paste the client_id and client_secret when asked.
    The script then opens a browser for consent and stores the refresh token
    so drive-upload.ps1 can refresh it automatically on each release.
#>

$ErrorActionPreference = "Stop"
$configPath = Join-Path $env:USERPROFILE ".dagboken-drive-config.json"

Write-Host ""
Write-Host "═══════════════════════════════════════════════════════════"
Write-Host "  Dagboken — Google Drive OAuth Setup"
Write-Host "═══════════════════════════════════════════════════════════"
Write-Host ""
Write-Host "You need an OAuth 2.0 client ID (Desktop app type)."
Write-Host "Create one at: https://console.cloud.google.com/apis/credentials"
Write-Host "Enable the Google Drive API first if you haven't:"
Write-Host "  https://console.cloud.google.com/apis/library/drive.googleapis.com"
Write-Host ""

$clientId     = Read-Host "Paste your client_id"
$clientSecret = Read-Host "Paste your client_secret"

$scope = "https://www.googleapis.com/auth/drive.file"

# Request device code
$deviceResp = Invoke-RestMethod -Method Post `
    -Uri "https://oauth2.googleapis.com/device/code" `
    -ContentType "application/x-www-form-urlencoded" `
    -Body "client_id=$clientId&scope=$scope"

Write-Host ""
Write-Host "─────────────────────────────────────────────────────────────"
Write-Host "  Open this URL in your browser and enter the code below:"
Write-Host ""
Write-Host "  URL:  $($deviceResp.verification_url)"
Write-Host "  Code: $($deviceResp.user_code)"
Write-Host "─────────────────────────────────────────────────────────────"
Write-Host ""

# Try to open the browser automatically
try { Start-Process $deviceResp.verification_url } catch {}

Write-Host "Waiting for you to authorise in the browser..."

$interval   = [int]$deviceResp.interval
$deviceCode = $deviceResp.device_code
$deadline   = (Get-Date).AddSeconds([int]$deviceResp.expires_in)
$tokenResp  = $null

while ((Get-Date) -lt $deadline) {
    Start-Sleep -Seconds $interval
    try {
        $tokenResp = Invoke-RestMethod -Method Post `
            -Uri "https://oauth2.googleapis.com/token" `
            -ContentType "application/x-www-form-urlencoded" `
            -Body "client_id=$clientId&client_secret=$clientSecret&device_code=$deviceCode&grant_type=urn:ietf:params:oauth:grant-type:device_code"
        break
    } catch {
        $body = $_.ErrorDetails.Message | ConvertFrom-Json -ErrorAction SilentlyContinue
        if ($body.error -eq "authorization_pending") { continue }
        if ($body.error -eq "slow_down") { $interval += 5; continue }
        Write-Error "OAuth error: $($body.error) — $($body.error_description)"
        exit 1
    }
}

if (-not $tokenResp) {
    Write-Error "Timed out waiting for authorisation. Re-run the script to try again."
    exit 1
}

$config = @{
    client_id     = $clientId
    client_secret = $clientSecret
    refresh_token = $tokenResp.refresh_token
}

$config | ConvertTo-Json | Set-Content -Path $configPath -Encoding UTF8

Write-Host ""
Write-Host "✓ Credentials saved to $configPath"
Write-Host "  drive-upload.ps1 will use these automatically on every release."
Write-Host ""
