$ErrorActionPreference = "Stop"
$ppt = New-Object -ComObject PowerPoint.Application
$templatePath = (Resolve-Path "SE Project PresentationTemplate.pptx").Path
$outputPath = Join-Path (Get-Location) "presentation_skeleton.pptx"

if (Test-Path $outputPath) {
    Remove-Item $outputPath -Force
}

Copy-Item $templatePath $outputPath -Force

$pres = $ppt.Presentations.Open($outputPath)

# Original template has 5 slides:
# 1: Cover
# 2: Contents
# 3: Literature Review (Table)
# 4: Comparison of Models (Table)
# 5: References

# Target layout:
# Slide 1: Cover
# Slide 2: Contents
# Slide 3: Introduction (dup of 3)
# Slide 4: Motivation (dup of 3)
# Slide 5: Problem Statement (dup of 3)
# Slide 6: Objectives (dup of 3)
# Slide 7: Literature Review Part 1 (original 3)
# Slide 8: Literature Review Part 2 (dup of 3)
# Slide 9: Comparison of Models (original 4)
# Slide 10: Research Gaps (dup of 3)
# Slide 11: Scope of Project (dup of 3)
# Slide 12: Methodology Workflow (dup of 3)
# Slide 13: Methodology Constraints (dup of 3)
# Slide 14: Methodology Architecture (dup of 3)
# Slide 15: Results Benchmarks (dup of 3)
# Slide 16: Results Quality Audit (dup of 3)
# Slide 17: References (original 5)

# We need 12 duplicates of Slide 3.
for ($i = 1; $i -le 12; $i++) {
    $dup = $pres.Slides.Item(3).Duplicate()
}

# Move Comparison of Models (originally slide 4, now shifted) to position 9
# Move References to position 17
# Let's verify slide names and order
Write-Host "Total slides before reorder: $($pres.Slides.Count)"

# Currently:
# 1: Cover
# 2: Contents
# 3: Lit Review
# 4: Lit Review (dup 1)
# ...
# 15: Lit Review (dup 12)
# 16: Comparison of Models
# 17: References

$pres.Slides.Item(16).MoveTo(9)

Write-Host "Total slides after reorder: $($pres.Slides.Count)"

$pres.Save()
$pres.Close()
$ppt.Quit()

Write-Host "Skeleton created successfully at $outputPath"
