$ErrorActionPreference = "Continue"
$out = "D:\.1 Work Stations\RootMC\scripts\local-edge\logs\term-direct.txt"
"launch $(Get-Date -Format o)" | Set-Content $out
try {
    & "D:\.1 Work Stations\RootMC\scripts\local-edge\Start-EdgeTerminal.ps1" `
        -ConfigPath "D:\.1 Work Stations\RootMC\scripts\local-edge\config.json" `
        -HttpPort 18792
    "returned normally" | Add-Content $out
} catch {
    "exception: $($_.Exception.Message)" | Add-Content $out
    "$_" | Add-Content $out
    $_.ScriptStackTrace | Add-Content $out
}
