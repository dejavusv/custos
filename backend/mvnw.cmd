@echo off
setlocal

if "%JAVA_HOME%"=="" (
    if exist "C:\Program Files\Microsoft\jdk-21.0.12.101-hotspot" (
        set "JAVA_HOME=C:\Program Files\Microsoft\jdk-21.0.12.101-hotspot"
    )
)

if exist "C:\Users\user\.maven\apache-maven-3.9.16\bin\mvn.cmd" (
    call "C:\Users\user\.maven\apache-maven-3.9.16\bin\mvn.cmd" %*
    exit /b %ERRORLEVEL%
)

mvn %*
exit /b %ERRORLEVEL%
