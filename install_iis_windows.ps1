# IIS Installation and Configuration Script
Write-Host "===== Starting IIS Installation & Provisioning ====="

# Enable IIS features
Write-Host "Enabling IIS features..."
Enable-WindowsOptionalFeature -Online -FeatureName IIS-WebServerRole -NoRestart
Enable-WindowsOptionalFeature -Online -FeatureName IIS-WebServer -NoRestart
Enable-WindowsOptionalFeature -Online -FeatureName IIS-CommonHttpFeatures -NoRestart
Enable-WindowsOptionalFeature -Online -FeatureName IIS-DefaultDocument -NoRestart
Enable-WindowsOptionalFeature -Online -FeatureName IIS-DirectoryBrowsing -NoRestart
Enable-WindowsOptionalFeature -Online -FeatureName IIS-HttpErrors -NoRestart
Enable-WindowsOptionalFeature -Online -FeatureName IIS-StaticContent -NoRestart

# Start IIS
Write-Host "Starting IIS..."
Start-Service W3SVC
Set-Service W3SVC -StartupType Automatic

# Create custom HTML file
Write-Host "Deploying custom HTML..."
$html = @"
<!DOCTYPE html>
<html>
<head>
    <title>Multi-Cloud Packer Image</title>
    <style>
        body { font-family: Arial, sans-serif; margin: 50px; background: #f0f0f0; }
        .container { background: white; padding: 30px; border-radius: 10px; box-shadow: 0 0 10px rgba(0,0,0,0.1); }
        h1 { color: #333; }
        p { color: #666; }
        .success { color: green; font-weight: bold; }
    </style>
</head>
<body>
    <div class="container">
        <h1>Welcome to Multi-Cloud Packer</h1>
        <p><span class="success">✓ IIS Server is running</span></p>
        <p>Image built with Packer at $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')</p>
        <p>Server: $(hostname)</p>
        <p>OS: $(Get-WmiObject -Class Win32_OperatingSystem | Select-Object -ExpandProperty Caption)</p>
    </div>
</body>
</html>
"@

$webroot = "C:\inetpub\wwwroot"
if (-not (Test-Path $webroot)) {
    New-Item -ItemType Directory -Path $webroot -Force | Out-Null
}

$html | Out-File -FilePath "$webroot\index.html" -Encoding UTF8 -Force

Write-Host "IIS provisioning completed successfully!"
