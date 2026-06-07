@echo off
set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr
call gradlew.bat :app:testDebugUnitTest --tests "com.skygoto.app.domain.model.SolarPositionCalculatorTest" --no-daemon --console=plain > test_output.log 2>&1
echo EXIT_CODE=%ERRORLEVEL%
