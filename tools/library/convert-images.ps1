# Convierte las ilustraciones de la biblioteca a JPEG calidad 80 con el lado mayor <= 1280 px (mismo
# criterio que AndroidImageStore). Lo llama build-backup.mjs con un JSON [{ source, target }].
# Windows PowerShell 5.1 (System.Drawing).
param([Parameter(Mandatory = $true)][string]$Map)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$maxSide = 1280
$quality = 80
$codec = [System.Drawing.Imaging.ImageCodecInfo]::GetImageEncoders() | Where-Object { $_.MimeType -eq 'image/jpeg' }
$params = New-Object System.Drawing.Imaging.EncoderParameters(1)
$params.Param[0] = New-Object System.Drawing.Imaging.EncoderParameter([System.Drawing.Imaging.Encoder]::Quality, [long]$quality)

$items = Get-Content -Raw -Encoding UTF8 $Map | ConvertFrom-Json
foreach ($item in $items) {
    $source = [System.Drawing.Image]::FromFile($item.source)
    try {
        $scale = [Math]::Min(1.0, $maxSide / [Math]::Max($source.Width, $source.Height))
        $width = [int][Math]::Round($source.Width * $scale)
        $height = [int][Math]::Round($source.Height * $scale)
        $bitmap = New-Object System.Drawing.Bitmap($width, $height)
        try {
            $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
            # Fondo blanco: las PNG con transparencia no quedan en negro al pasar a JPEG.
            $graphics.Clear([System.Drawing.Color]::White)
            $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
            $graphics.DrawImage($source, 0, 0, $width, $height)
            $graphics.Dispose()
            New-Item -ItemType Directory -Force (Split-Path $item.target) | Out-Null
            $bitmap.Save($item.target, $codec, $params)
        } finally {
            $bitmap.Dispose()
        }
    } finally {
        $source.Dispose()
    }
}
Write-Output ("Ilustraciones convertidas: " + @($items).Count)
