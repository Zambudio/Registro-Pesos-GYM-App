# Convierte las imágenes extraídas por extract-catalog.mjs (tools/catalog/raw) a JPEG calidad 80,
# lado mayor <= 1280 px (mismo criterio que AndroidImageStore), en app/src/main/assets/catalog/images.
# Windows PowerShell 5.1 (System.Drawing). Borra antes las imágenes que hubiera en el destino.
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$root = Resolve-Path (Join-Path $PSScriptRoot '..\..')
$raw = Join-Path $PSScriptRoot 'raw'
$out = Join-Path $root 'app\src\main\assets\catalog\images'
$maxSide = 1280
$quality = 80

if (Test-Path $out) { Remove-Item -Recurse -Force $out }
New-Item -ItemType Directory -Force $out | Out-Null

$codec = [System.Drawing.Imaging.ImageCodecInfo]::GetImageEncoders() | Where-Object { $_.MimeType -eq 'image/jpeg' }
$params = New-Object System.Drawing.Imaging.EncoderParameters(1)
$params.Param[0] = New-Object System.Drawing.Imaging.EncoderParameter([System.Drawing.Imaging.Encoder]::Quality, [long]$quality)

Get-ChildItem $raw -File | ForEach-Object {
    $source = [System.Drawing.Image]::FromFile($_.FullName)
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
            $target = Join-Path $out ($_.BaseName + '.jpg')
            $bitmap.Save($target, $codec, $params)
        } finally {
            $bitmap.Dispose()
        }
    } finally {
        $source.Dispose()
    }
}
Write-Output ("Imágenes convertidas: " + (Get-ChildItem $out -File).Count)
