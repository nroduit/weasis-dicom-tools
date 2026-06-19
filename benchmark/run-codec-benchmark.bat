@echo off
rem ---------------------------------------------------------------------------
rem Runs org.dcm4che3.img.bench.CodecBenchmark in a forked JVM (Windows x64).
rem exec:java runs in-process and ignores -Djava.library.path, so the OpenCV
rem native library never loads — hence this launcher.
rem
rem Usage:
rem   run-codec-benchmark.bat <sample-dir> [warmup] [iterations]
rem
rem   Transcode re-encodes each file back to its own (source) codec — a same-codec round-trip —
rem   so the decoder and encoder always match. There is no destination-syntax argument.
rem
rem Env overrides:
rem   OUT=<file.csv>          also write the CSV to this file; a <file>.json build-metadata
rem                          sidecar (host CPU, OS/arch, git commit) is written next to it
rem   WEASIS_VERSION=<x.y.z>  rebuild weasis-dicom-tools against this weasis-core-img version first
rem   JAVA_OPTS=...           JVM options (default: -Xms2g -Xmx8g)
rem   OFFLINE=0              build online instead of offline (-o); set on a fresh clone
rem                          so Maven can download deps/native into ~/.m2 the first time
rem
rem A/B example (cmd.exe):
rem   set OUT=candidate.csv & run-codec-benchmark.bat D:\samples
rem   set OUT=baseline.csv & set WEASIS_VERSION=4.12.0.1 & run-codec-benchmark.bat D:\samples
rem ---------------------------------------------------------------------------
setlocal enabledelayedexpansion

set "MODULE_DIR=%~dp0"
if "%MODULE_DIR:~-1%"=="\" set "MODULE_DIR=%MODULE_DIR:~0,-1%"
for %%I in ("%MODULE_DIR%\..") do set "REPO_ROOT=%%~fI"
if not defined MVN set "MVN=mvn"

set "SAMPLE_DIR=%~f1"
set "WARMUP=%~2"
if "%WARMUP%"=="" set "WARMUP=2"
set "ITERS=%~3"
if "%ITERS%"=="" set "ITERS=5"
if not defined JAVA_OPTS set "JAVA_OPTS=-Xms2g -Xmx8g"
set "OFF=-o"
if "%OFFLINE%"=="0" set "OFF="

if "%SAMPLE_DIR%"=="" (
  echo Usage: %~nx0 ^<sample-dir^> [warmup] [iterations]
  echo   env: OUT=^<file.csv^>  WEASIS_VERSION=^<x.y.z^>  JAVA_OPTS=-Xmx8g
  exit /b 2
)

rem Make OUT absolute before changing directory, then run from the reactor root (-pl resolves there).
if defined OUT for %%O in ("%OUT%") do set "OUT=%%~fO"
cd /d "%REPO_ROOT%"

set "NATIVE_DIR_NAME=windows-x86-64"

set "VFLAG="
if defined WEASIS_VERSION set "VFLAG=-Dweasis.core.img.version=%WEASIS_VERSION%"

echo # building (weasis-core-img=%WEASIS_VERSION%) ... 1>&2
call "%MVN%" %OFF% -q install -DskipTests %VFLAG% -pl weasis-dicom-tools -am || exit /b 1
call "%MVN%" %OFF% -q process-classes %VFLAG% -pl benchmark || exit /b 1

set "LIB_DIR=%MODULE_DIR%\target\lib\%NATIVE_DIR_NAME%"
if not exist "%LIB_DIR%" (
  echo Native lib dir not found: %LIB_DIR% 1>&2
  exit /b 1
)

set "CP_FILE=%MODULE_DIR%\target\codec-bench-cp.txt"
call "%MVN%" %OFF% -q dependency:build-classpath %VFLAG% -pl benchmark -Dmdep.includeScope=runtime -Dmdep.outputFile="%CP_FILE%" || exit /b 1
set /p DEPCP=<"%CP_FILE%"
set "CP=%MODULE_DIR%\target\classes;%DEPCP%"

if not defined WEASIS_VERSION set "WEASIS_VERSION=default"

set "JAVA_CMD=java %JAVA_OPTS% -Dweasis.core.img.version=%WEASIS_VERSION% -Djava.library.path=^"%LIB_DIR%^" --enable-native-access=ALL-UNNAMED -cp ^"%CP%^" org.dcm4che3.img.bench.CodecBenchmark ^"%SAMPLE_DIR%^" %WARMUP% %ITERS%"

if defined OUT (
  %JAVA_CMD% > "%OUT%"
  rem Build-metadata sidecar next to the CSV (parity with the CI run).
  set "OUT_JSON=%OUT%"
  if /i "%OUT:~-4%"==".csv" set "OUT_JSON=%OUT:~0,-4%"
  powershell -NoProfile -ExecutionPolicy Bypass -File "%MODULE_DIR%\collect-metadata.ps1" -Classifier windows-x86-64 -Out "%OUT_JSON%.json"
) else (
  %JAVA_CMD%
)
endlocal