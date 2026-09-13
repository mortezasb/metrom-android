param(
  [string]$Keystore = "metrom-upload.jks",
  [string]$Alias = "metrom"
)
keytool -list -v -keystore $Keystore -alias $Alias | Select-String "SHA256:"
