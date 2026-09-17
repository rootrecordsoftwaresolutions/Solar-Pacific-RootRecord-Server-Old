@echo off
REM Build plugins — Gradle runs on JDK 17+; bytecode targets Java 25 via toolchain (gradle.properties).
setlocal EnableDelayedExpansion
cd /d "%~dp0"

REM Optional: org.gradle.java.home in local.properties selects the Gradle daemon JVM (must NOT be JDK 25).
if exist "local.properties" (
  for /f "usebackq eol=# tokens=1,* delims==" %%A in ("local.properties") do (
    if /i "%%A"=="org.gradle.java.home" (
      set "JAVA_HOME=%%B"
      if exist "!JAVA_HOME!\bin\java.exe" (
        echo Using Gradle JVM JAVA_HOME=!JAVA_HOME!
        set "PATH=!JAVA_HOME!\bin;!PATH!"
      ) else (
        echo WARN: org.gradle.java.home not found: !JAVA_HOME!
      )
    )
  )
)

call gradlew.bat %*
exit /b %ERRORLEVEL%
