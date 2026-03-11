@echo off
set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr
set ANDROID_HOME=C:\Users\33678\AppData\Local\Android\Sdk
set PATH=%JAVA_HOME%\bin;%PATH%
echo JAVA_HOME=%JAVA_HOME%
echo ANDROID_HOME=%ANDROID_HOME%
java -version
call gradlew.bat assembleDebug
echo BUILD EXIT CODE: %ERRORLEVEL%
