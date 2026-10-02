<#
.SYNOPSIS
    Dismod Live Log Retriever for AI Diagnostics and Developers.
    Fetches real-time structured JSONL logs directly from Dismod running on an Android device or emulator.

.DESCRIPTION
    Tries multiple fallback mechanisms to retrieve logs directly:
    1. Direct HTTP request (Wi-Fi or existing forwarded port)
    2. Automatic ADB port forwarding (`adb forward tcp:8088 tcp:8088`) + HTTP
    3. Direct ADB `run-as` file read from internal storage
    4. Direct ADB external storage read (`/sdcard/Android/data/com.dismod.app.debug/files/logs/app.log`)
    5. ADB Logcat filtered by tag `AppLogger`

.PARAMETER Ip
    Device Wi-Fi IP address (as displayed in Settings -> Debug Logs). If omitted, connects locally.

.PARAMETER Port
    DebugLogServer port (default: 8088).

.PARAMETER ErrorsOnly
    Retrieve only ERROR and WARN level entries.

.PARAMETER Limit
    Maximum number of log entries to retrieve (default: 200).

.PARAMETER Raw
    Retrieve all raw JSONL lines without AI header.

.PARAMETER Clear
    Clear all logs on the device.

.PARAMETER Status
    Fetch current app lifecycle status, screen, and session details.

.PARAMETER OutFile
    Optional file path to save the retrieved logs.

.EXAMPLE
    .\tools\fetch-logs.ps1
    .\tools\fetch-logs.ps1 -ErrorsOnly
    .\tools\fetch-logs.ps1 -Ip 192.168.1.105
    .\tools\fetch-logs.ps1 -OutFile latest_logs.txt
    .\tools\fetch-logs.ps1 -Status
    .\tools\fetch-logs.ps1 -Clear
#>

[CmdletBinding()]
param (
    [string]$Ip = "",
    [int]$Port = 8088,
    [switch]$ErrorsOnly,
    [int]$Limit = 200,
    [switch]$Raw,
    [switch]$Clear,
    [switch]$Status,
    [string]$CloudUrl = "",
    [switch]$FromCloud,
    [string]$Password = "DismodLogs#2026",
    [string]$OutFile = "",
    [string]$AdbPath = ""
)

$ErrorActionPreference = "Continue"

# ── 1. Locate ADB ──
function Find-Adb {
    if ($AdbPath -and (Test-Path $AdbPath)) { return $AdbPath }
    if (Get-Command "adb" -ErrorAction SilentlyContinue) { return "adb" }

    $candidates = @(
        "E:\android-sdk\platform-tools\adb.exe",
        "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe",
        "$env:ANDROID_HOME\platform-tools\adb.exe",
        "$env:ProgramFiles\Android\platform-tools\adb.exe"
    )
    foreach ($cand in $candidates) {
        if (Test-Path $cand) { return $cand }
    }
    return $null
}

$adb = Find-Adb

# ── 2. Determine Endpoint URL ──
$hostAddr = if ($Ip) { $Ip } else { "localhost" }
$endpointPath = if ($Status) {
    "/status"
} elseif ($Clear) {
    "/logs/clear"
} elseif ($Raw) {
    "/logs/raw"
} elseif ($ErrorsOnly) {
    "/logs/errors?limit=$Limit"
} else {
    "/logs?limit=$Limit"
}

$httpUrl = "http://${hostAddr}:${Port}${endpointPath}"

# ── 3. Helper: Try HTTP Fetch ──
function Try-HttpFetch([string]$url) {
    try {
        $response = Invoke-RestMethod -Uri $url -Method Get -TimeoutSec 3 -ErrorAction Stop
        return $response
    } catch {
        return $null
    }
}

# Strategy 0: Direct Cloud Download (from anywhere on the internet)
if ($CloudUrl -or $FromCloud) {
    $targetCloudUrl = $CloudUrl
    if (-not $targetCloudUrl -and $FromCloud) {
        Write-Host "Querying device for latest cloud log link..." -ForegroundColor Cyan
        $devStatus = Try-HttpFetch "http://${hostAddr}:${Port}/logs/cloud"
        if ($devStatus -and $devStatus.url) {
            $targetCloudUrl = $devStatus.url
        }
    }
    if ($targetCloudUrl) {
        Write-Host "Fetching logs directly from cloud storage ($targetCloudUrl)..." -ForegroundColor Cyan
        $rawCloudUrl = $targetCloudUrl
        if ($targetCloudUrl -match "^https?://dpaste\.org/[a-zA-Z0-9]+$" -and -not $targetCloudUrl.EndsWith("/raw")) {
            $rawCloudUrl = "$targetCloudUrl/raw"
        }
        try {
            $result = Invoke-RestMethod -Uri $rawCloudUrl -Method Get -TimeoutSec 10 -ErrorAction Stop
        } catch {
            Write-Error "Failed fetching from cloud URL: $_"
            exit 1
        }
    } else {
        Write-Error "No cloud URL specified and could not query device for latest cloud log URL."
        exit 1
    }
}

if (-not $result) {
    Write-Host "Fetching Dismod logs from $httpUrl..." -ForegroundColor Cyan

    # Strategy 1: Direct HTTP (works if IP is specified or port already forwarded)
    $result = Try-HttpFetch $httpUrl
}

# Strategy 2: If localhost failed and ADB exists, attempt ADB forward
if (-not $result -and -not $Ip -and $adb) {
    Write-Host "Direct HTTP unreachable. Checking ADB connection..." -ForegroundColor Yellow
    try {
        $devices = & $adb devices
        if ($devices -match "(?m)^([^\s]+)\s+device$") {
            Write-Host "Device detected via ADB. Setting up port forwarding (tcp:$Port -> tcp:$Port)..." -ForegroundColor Yellow
            & $adb forward tcp:$Port tcp:$Port
            Start-Sleep -Milliseconds 300
            $result = Try-HttpFetch "http://localhost:${Port}${endpointPath}"
        }
    } catch {
        Write-Warning "ADB forward attempt failed: $_"
    }
}

# Strategy 3: Direct internal storage read via run-as
if (-not $result -and $adb -and -not $Status -and -not $Clear) {
    Write-Host "Attempting ADB run-as cat files/logs/app.log..." -ForegroundColor Yellow
    $packageNames = @("com.dismod.app.debug", "chat.stoat", "com.dismod.app")
    foreach ($pkg in $packageNames) {
        try {
            $catOut = & $adb exec-out run-as $pkg cat files/logs/app.log 2>$null
            if ($catOut -and $catOut.Length -gt 10) {
                Write-Host "Successfully read logs via ADB run-as ($pkg)!" -ForegroundColor Green
                $result = $catOut -join "`n"
                break
            }
        } catch {}
    }
}

# Strategy 4: External storage read
if (-not $result -and $adb -and -not $Status -and -not $Clear) {
    Write-Host "Attempting ADB external storage read..." -ForegroundColor Yellow
    $paths = @(
        "/sdcard/Android/data/com.dismod.app.debug/files/logs/app.log",
        "/sdcard/Android/data/chat.stoat/files/logs/app.log"
    )
    foreach ($p in $paths) {
        try {
            $extOut = & $adb exec-out cat $p 2>$null
            if ($extOut -and $extOut.Length -gt 10) {
                Write-Host "Successfully read logs via ADB external storage!" -ForegroundColor Green
                $result = $extOut -join "`n"
                break
            }
        } catch {}
    }
}

# Strategy 5: Logcat fallback
if (-not $result -and $adb -and -not $Status -and -not $Clear) {
    Write-Host "Attempting ADB logcat filter (AppLogger:V)..." -ForegroundColor Yellow
    try {
        $logcatOut = & $adb logcat -d -s AppLogger:V 2>$null
        if ($logcatOut -and $logcatOut.Length -gt 10) {
            Write-Host "Successfully captured logs via ADB logcat!" -ForegroundColor Green
            $result = $logcatOut -join "`n"
        }
    } catch {}
}

# ── 4. Helper: Decrypt Log If Encrypted ──
function Decrypt-DismodLog([string]$rawText, [string]$pwd) {
    if (-not $rawText -or -not $rawText.Trim().StartsWith("{") -or $rawText -notmatch '"dismod_encrypted"\s*:\s*true') {
        return $rawText
    }

    try {
        $json = $rawText | ConvertFrom-Json
        $iv = [System.Convert]::FromBase64String($json.iv)
        $cipherBytes = [System.Convert]::FromBase64String($json.data)

        $sha256 = [System.Security.Cryptography.SHA256]::Create()
        $keyBytes = $sha256.ComputeHash([System.Text.Encoding]::UTF8.GetBytes($pwd))

        $aes = [System.Security.Cryptography.Aes]::Create()
        $aes.Mode = [System.Security.Cryptography.CipherMode]::CBC
        $aes.Padding = [System.Security.Cryptography.PaddingMode]::PKCS7
        $aes.Key = $keyBytes
        $aes.IV = $iv

        $decryptor = $aes.CreateDecryptor()
        $plainBytes = $decryptor.TransformFinalBlock($cipherBytes, 0, $cipherBytes.Length)
        Write-Host "Successfully decrypted cloud log using password!" -ForegroundColor Green
        return [System.Text.Encoding]::UTF8.GetString($plainBytes)
    } catch {
        Write-Warning "Failed to decrypt log with password '$pwd': $($_.Exception.Message)"
        Write-Warning "If a custom password was configured in Dismod Settings -> Debug Logs, specify it using -Password <pwd>"
        return $rawText
    }
}

# ── 5. Process and Output ──
if ($result) {
    $outString = if ($result -is [string]) { $result } else { $result | ConvertTo-Json -Depth 5 }
    $outString = Decrypt-DismodLog $outString $Password

    if ($OutFile) {
        $resolvedOut = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($OutFile)
        [System.IO.File]::WriteAllText($resolvedOut, $outString, [System.Text.Encoding]::UTF8)
        Write-Host "Successfully saved logs to: $resolvedOut" -ForegroundColor Green
    } else {
        Write-Output $outString
    }
} else {
    Write-Error @"
Failed to retrieve Dismod logs.
Possible causes:
  1. The app is not currently running on the device or emulator.
  2. If using Wi-Fi, ensure your phone and PC are on the same network and pass -Ip <phone_ip>.
  3. If using USB, enable USB Debugging on your device and connect it via USB.
"@
    exit 1
}
