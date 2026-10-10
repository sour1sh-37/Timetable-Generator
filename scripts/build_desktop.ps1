# SIES Timetable Generator - Full Windows Desktop Packaging Pipeline
$ErrorActionPreference = "Stop"

Write-Host "================================================================="
Write-Host " SIES Timetable Generator - Windows Desktop Packaging Pipeline"
Write-Host "================================================================="

# Step 1: Maven Backend Compilation & Dependency Bundling
Write-Host ""
Write-Host "[Step 1/4] Building Java backend and copying runtime dependencies..."
mvn package -DskipTests

# Step 2: Resource Staging & Private JRE Creation
Write-Host ""
Write-Host "[Step 2/4] Generating private JRE and staging bundled resources..."
python scripts/prepare_resources.py

# Step 3: Frontend Production Build
Write-Host ""
Write-Host "[Step 3/4] Building production React frontend..."
Push-Location frontend
npm run build
Pop-Location

# Step 4: Tauri Desktop Packaging & NSIS Installer Generation
Write-Host ""
Write-Host "[Step 4/4] Building Tauri Windows Application and NSIS Installer..."
npx @tauri-apps/cli build

Write-Host ""
Write-Host "================================================================="
Write-Host " [SUCCESS] Desktop packaging completed successfully!"
Write-Host "================================================================="
