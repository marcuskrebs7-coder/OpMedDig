# Tegner Play-grafik: ikon 512x512 og feature-grafik 1024x500 (PNG).
# Kør: powershell -ExecutionPolicy Bypass -File play-listing\make-graphics.ps1
Add-Type -AssemblyName System.Drawing
$out = $PSScriptRoot
$orange = [System.Drawing.Color]::FromArgb(255, 255, 90, 31)
$orange2 = [System.Drawing.Color]::FromArgb(255, 255, 45, 85)
$white = [System.Drawing.Color]::White

function Draw-Clock($g, [float]$cx, [float]$cy, [float]$r, [float]$w) {
    $pen = New-Object System.Drawing.Pen($white, $w)
    $pen.StartCap = 'Round'; $pen.EndCap = 'Round'; $pen.LineJoin = 'Round'
    $g.DrawEllipse($pen, $cx - $r, $cy - $r, 2 * $r, 2 * $r)
    # visere
    $pts = [System.Drawing.PointF[]]@(
        (New-Object System.Drawing.PointF($cx, ($cy - $r * 0.58))),
        (New-Object System.Drawing.PointF($cx, $cy)),
        (New-Object System.Drawing.PointF(($cx + $r * 0.38), ($cy + $r * 0.25))))
    $g.DrawLines($pen, $pts)
    # ringe-streger
    $p2 = New-Object System.Drawing.Pen($white, ($w * 0.9)); $p2.StartCap = 'Round'; $p2.EndCap = 'Round'
    $g.DrawLine($p2, ($cx - $r * 1.0), ($cy - $r * 0.83), ($cx - $r * 0.75), ($cy - $r * 1.08))
    $g.DrawLine($p2, ($cx + $r * 1.0), ($cy - $r * 0.83), ($cx + $r * 0.75), ($cy - $r * 1.08))
}

# --- Ikon 512x512 (Play lægger selv runde hjørner på) ---
$bmp = New-Object System.Drawing.Bitmap(512, 512)
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.SmoothingMode = 'AntiAlias'
$g.Clear($orange)
Draw-Clock $g 256 270 128 26
$bmp.Save("$out\icon-512.png", [System.Drawing.Imaging.ImageFormat]::Png)
$g.Dispose(); $bmp.Dispose()

# --- Feature-grafik 1024x500 ---
$bmp = New-Object System.Drawing.Bitmap(1024, 500)
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.SmoothingMode = 'AntiAlias'; $g.TextRenderingHint = 'AntiAliasGridFit'
$rect = New-Object System.Drawing.Rectangle(0, 0, 1024, 500)
$grad = New-Object System.Drawing.Drawing2D.LinearGradientBrush($rect, $orange, $orange2, 20)
$g.FillRectangle($grad, $rect)
Draw-Clock $g 230 260 120 24
$brush = New-Object System.Drawing.SolidBrush($white)
$title = New-Object System.Drawing.Font("Segoe UI Black", 72, [System.Drawing.FontStyle]::Bold, [System.Drawing.GraphicsUnit]::Pixel)
$sub = New-Object System.Drawing.Font("Segoe UI Semibold", 34, [System.Drawing.FontStyle]::Regular, [System.Drawing.GraphicsUnit]::Pixel)
$g.DrawString("Op Med Dig", $title, $brush, 420, 150)
# "æ" skrives som tegnkode, fordi Windows PowerShell læser scriptet som ANSI
$g.DrawString(("V" + [char]0xE6 + "kkeuret du ikke kan"), $sub, $brush, 426, 260)
$g.DrawString("snooze dig fra", $sub, $brush, 426, 304)
$bmp.Save("$out\feature-1024x500.png", [System.Drawing.Imaging.ImageFormat]::Png)
$g.Dispose(); $bmp.Dispose()
Write-Host "Lavet: icon-512.png og feature-1024x500.png i $out"
