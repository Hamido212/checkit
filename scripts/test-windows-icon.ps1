$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName PresentationCore
$iconPath = Join-Path (Split-Path -Parent $PSScriptRoot) 'windows\Assets\checkit.ico'
$stream = [System.IO.File]::OpenRead($iconPath)
try {
    # Explorer accepting an ICO does not mean WPF's image decoder accepts it.
    $decoder = [System.Windows.Media.Imaging.BitmapDecoder]::Create(
        $stream,
        [System.Windows.Media.Imaging.BitmapCreateOptions]::PreservePixelFormat,
        [System.Windows.Media.Imaging.BitmapCacheOption]::OnLoad)
    if ($decoder.Frames.Count -lt 6) { throw 'Expected all six icon sizes.' }
    foreach ($frame in $decoder.Frames) {
        if ($frame.PixelWidth -le 0 -or $frame.PixelHeight -le 0) { throw 'Invalid icon frame.' }
    }
    Write-Output "WPF decoded all $($decoder.Frames.Count) icon frames."
} finally { $stream.Dispose() }
