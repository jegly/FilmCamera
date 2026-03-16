$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:ANDROID_HOME = "$env:USERPROFILE\AppData\Local\Android\Sdk"
$env:PATH = $env:JAVA_HOME + "\bin;" + $env:PATH

Set-Location $PSScriptRoot

Write-Host "JAVA_HOME: $env:JAVA_HOME"
Write-Host "ANDROID_HOME: $env:ANDROID_HOME"

& "$env:JAVA_HOME\bin\java.exe" -version

& ".\gradlew.bat" assembleDebug
