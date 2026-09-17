# Generate Android launcher icon bitmaps from the user's 128x128 PNG using System.Drawing.
# Outputs (xxxhdpi = 4x of a 108dp canvas):
#   ic_launcher_foreground.png  transparent canvas + source centered at 288px (=72dp, adaptive safe zone)
#   ic_launcher.png             white rounded-square plate (24dp radius) + source centered 288px (API 23-25)
#   ic_launcher_round.png       white circular plate + source centered 288px (API 25 round)
# NOTE: keep this script pure ASCII; PowerShell 5.1 misreads UTF-8 without BOM as GBK.
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$srcPath = 'E:\Desktop\shortcuts_icon_156793.png'
$outDir  = 'E:\Agent\kuaijie-tmp\mipmap-xxxhdpi'
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

$src = [System.Drawing.Image]::FromFile($srcPath)

function New-Bitmap {
    param([int]$Size)
    $bmp = New-Object System.Drawing.Bitmap($Size, $Size, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    return @($bmp, $g)
}

function Save-Png {
    param($bmp, [string]$path)
    $bmp.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
}

# 1) adaptive foreground: transparent canvas, source 288x288 centered (72px bleed on each side)
$r = New-Bitmap 432
$r[1].DrawImage($src, 72, 72, 288, 288)
Save-Png $r[0] (Join-Path $outDir 'ic_launcher_foreground.png')
$r[1].Dispose()

# 2) legacy square: white rounded rect (radius 24dp = 96px) + source 288x288 centered
$r = New-Bitmap 432
$path = New-Object System.Drawing.Drawing2D.GraphicsPath
$d = 96 * 2
$path.AddArc(0, 0, $d, $d, 180, 90)
$path.AddArc(432 - $d, 0, $d, $d, 270, 90)
$path.AddArc(432 - $d, 432 - $d, $d, $d, 0, 90)
$path.AddArc(0, 432 - $d, $d, $d, 90, 90)
$path.CloseFigure()
$r[1].FillPath([System.Drawing.Brushes]::White, $path)
$r[1].DrawImage($src, 72, 72, 288, 288)
Save-Png $r[0] (Join-Path $outDir 'ic_launcher.png')
$r[1].Dispose()

# 3) legacy round: white circle + source 288x288 centered
$r = New-Bitmap 432
$r[1].FillEllipse([System.Drawing.Brushes]::White, 0, 0, 432, 432)
$r[1].DrawImage($src, 72, 72, 288, 288)
Save-Png $r[0] (Join-Path $outDir 'ic_launcher_round.png')
$r[1].Dispose()

$src.Dispose()
Get-ChildItem $outDir | Select-Object Name, Length
