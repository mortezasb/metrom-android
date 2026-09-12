param([string]$Keystore = "metrom-upload.jks")
$base64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes($Keystore))
$base64 | Set-Clipboard
Write-Host "Keystore Base64 copied to clipboard. Add it as GitHub secret METROM_KEYSTORE_BASE64."
