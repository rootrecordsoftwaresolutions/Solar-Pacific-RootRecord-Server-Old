function Get-HostMetricsSample {
    $cpu = 0.0
    $ram = 0.0
    try {
        $cpuObj = Get-CimInstance Win32_Processor | Select-Object -First 1
        if ($cpuObj -and $null -ne $cpuObj.LoadPercentage) {
            $cpu = [double]$cpuObj.LoadPercentage
        }
    } catch { }
    try {
        $os = Get-CimInstance Win32_OperatingSystem
        if ($os -and $os.TotalVisibleMemorySize -gt 0) {
            $used = [double]($os.TotalVisibleMemorySize - $os.FreePhysicalMemory)
            $ram = ($used / [double]$os.TotalVisibleMemorySize) * 100.0
        }
    } catch { }
    $disk = 0.0
    try {
        $driveRoot = "${driveLetter}:\"
        if (Test-Path $driveRoot) {
            $di = Get-CimInstance Win32_LogicalDisk -Filter ("DeviceID='{0}'" -f $driveLetter)
            if ($di -and $di.Size -gt 0) {
                $disk = (([double]$di.Size - [double]$di.FreeSpace) / [double]$di.Size) * 100.0
            }
        } else {
            $sys = Get-CimInstance Win32_LogicalDisk -Filter "DeviceID='C:'"
            if ($sys -and $sys.Size -gt 0) {
                $disk = (([double]$sys.Size - [double]$sys.FreeSpace) / [double]$sys.Size) * 100.0
            }
        }
    } catch { }
    @{
        Cpu = [Math]::Max(0, [Math]::Min(100, $cpu))
        Ram = [Math]::Max(0, [Math]::Min(100, $ram))
        Disk = [Math]::Max(0, [Math]::Min(100, $disk))
    }
}

function Send-HostMetricsMinute {
    param(
        [array]$Samples,
        [datetime]$MinuteStart,
        [string]$WorkstationId,
        [string]$ApiBase,
        [string]$DevKey
    )
    if (-not $Samples -or $Samples.Count -eq 0) { return }
    $cpu = 0.0; $ram = 0.0; $disk = 0.0
    foreach ($s in $Samples) {
        $cpu += [double]$s.Cpu
        $ram += [double]$s.Ram
        $disk += [double]$s.Disk
    }
    $count = $Samples.Count
    $minuteTs = $MinuteStart.ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ss.fffZ")
    $payload = @{
        workstation_id = $WorkstationId
        minute_ts      = $minuteTs
        cpu_avg_pct    = [Math]::Round($cpu / $count, 2)
        ram_avg_pct    = [Math]::Round($ram / $count, 2)
        disk_used_pct  = [Math]::Round($disk / $count, 2)
        sample_count   = $count
    }
    $json = $payload | ConvertTo-Json -Compress
    $uri = "$($ApiBase.TrimEnd('/'))/api/rootmc/host-metrics/minute"
    $headers = @{
        Authorization = "Bearer $DevKey"
        Accept        = "application/json"
    }
    try {
        Invoke-RestMethod -Uri $uri -Method POST -Headers $headers -ContentType "application/json" -Body $json | Out-Null
    } catch {
        Write-Warning "host-metrics minute post failed: $($_.Exception.Message)"
    }
}