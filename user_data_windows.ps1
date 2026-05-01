# Enable WinRM for Packer communication
Write-Host "Enabling WinRM..."

# Configure WinRM
Set-NetConnectionProfile -NetworkCategory Private -Confirm:$false -ErrorAction SilentlyContinue
Enable-PSRemoting -Force -SkipNetworkProfileCheck

# Configure WinRM listener
$thumbprint = (Get-ChildItem cert:\LocalMachine\My | Where-Object {$_.Subject -match $env:COMPUTERNAME}).Thumbprint
if (-not $thumbprint) {
    Write-Host "Creating self-signed certificate..."
    $cert = New-SelfSignedCertificate -DnsName $env:COMPUTERNAME -CertStoreLocation "cert:\LocalMachine\My"
    $thumbprint = $cert.Thumbprint
}

Remove-WSManInstance -ResourceURI winrm/config/Listener -SelectorSet @{Address="*"; Transport="HTTPS"} -ErrorAction SilentlyContinue

New-WSManInstance -ResourceURI "winrm/config/Listener" -SelectorSet @{Address="*";Transport="HTTPS"} -ValueSet @{Hostname=$env:COMPUTERNAME;CertificateThumbprint=$thumbprint}

# Configure firewall
Write-Host "Configuring firewall..."
netsh advfirewall firewall add rule name="Allow WinRM HTTPS" dir=in action=allow protocol=tcp localport=5986 profile=any

# Set WinRM service to auto-start
Set-Service WinRM -StartupType Automatic
Start-Service WinRM

Write-Host "WinRM configuration complete"
