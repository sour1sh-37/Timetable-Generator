$ErrorActionPreference = "Stop"

$ppt = New-Object -ComObject PowerPoint.Application
$presPath = (Resolve-Path "SIES_Timetable_Generator_Presentation.pptx").Path
$pdfPath = Join-Path (Get-Location) "SIES_Timetable_Generator_Presentation.pdf"
$imagesDir = Join-Path (Get-Location) "presentation\slide_images"

if (-not (Test-Path $imagesDir)) {
    New-Item -ItemType Directory -Path $imagesDir -Force | Out-Null
}

Write-Host "Opening presentation in PowerPoint COM: $presPath"
$pres = $ppt.Presentations.Open($presPath)

Write-Host "1. Exporting presentation to PDF ($pdfPath)..."
# 32 = ppSaveAsPDF
$pres.SaveAs($pdfPath, 32)
Write-Host "   [OK] PDF exported successfully."

Write-Host "2. Exporting slide images (PNG) to $imagesDir..."
$pres.Export($imagesDir, "PNG", 1920, 1080)
Write-Host "   [OK] Slides exported to PNG successfully."

$pres.Close()
$ppt.Quit()

Write-Host "Export completed successfully!"
