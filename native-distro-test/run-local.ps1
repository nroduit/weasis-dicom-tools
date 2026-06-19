#!/usr/bin/env pwsh
# Windows counterpart of `run-matrix.sh --local`.
#
# Runs the SmokeTest on THIS machine using the windows-x86-64 native (opencv_java.dll) —
# no Docker, no OS-version check. The Windows *minimum* (Win 10) is encoded in the DLL
# (PE MajorSubsystemVersion; check with `dumpbin /headers opencv_java.dll`); this script
# is the runtime "does it load + decode here" smoke. Run from native-distro-test/:
#     pwsh ./run-local.ps1
#
# Requires: JDK 17+ (javac/java on PATH) and Maven, with the project's natives already in
# the local ~/.m2 (build the project once). All staged artifacts are arch-independent
# except the DLL, which is resolved from ~/.m2 offline.
$ErrorActionPreference = 'Stop'
$Here   = Split-Path -Parent $MyInvocation.MyCommand.Path
$Repo   = Split-Path -Parent $Here
$Dist   = Join-Path $Here 'dist'
$Cls    = 'windows-x86-64'
$Sample = 'jpeg2000-multiframe-multifragments.dcm'
$BeName = 'US-RGB-8-epicard-Banded-bigendian.dcm'
$BeHash = '4f31564042a349abd8e556b92b7121d25cc3c63fd0ac36a109b33922a38261d7'

function Run($file, $argline) { & $file @argline; if ($LASTEXITCODE -ne 0) { throw "$file failed ($LASTEXITCODE)" } }

# 1) Assemble the arch-independent dist (jars + module classes + samples + compiled SmokeTest).
New-Item -Force -ItemType Directory "$Dist\jars","$Dist\classes","$Dist\data","$Dist\lib\$Cls" | Out-Null
Run 'mvn' @('-q','-f',(Join-Path $Repo 'weasis-dicom-tools'),'-DskipTests','install',
            'dependency:copy-dependencies','-DincludeScope=runtime',"-DoutputDirectory=$Dist\jars")
Run 'mvn' @('-q','-f',(Join-Path $Repo 'weasis-dicom-tools'),
            'dependency:copy-dependencies','-DincludeArtifactIds=slf4j-api,slf4j-simple',"-DoutputDirectory=$Dist\jars")
Copy-Item -Recurse -Force "$Repo\weasis-dicom-tools\target\classes\*" "$Dist\classes\"
$res = "$Repo\weasis-dicom-tools\src\test\resources\org\dcm4che3\img"
Copy-Item -Force "$res\$Sample" "$Dist\data\$Sample"
if (Test-Path "$res\$BeName") { Copy-Item -Force "$res\$BeName" "$Dist\data\$BeName" }
Run 'javac' @('--release','17','-cp',"$Dist\classes;$Dist\jars\*",'-d',"$Dist\classes","$Here\SmokeTest.java")

# 2) Resolve the windows-x86-64 DLL from ~/.m2 (offline) and stage it as opencv_java.dll.
$cache = "$Here\.native-cache"
Remove-Item -Recurse -Force $cache -ErrorAction Ignore; New-Item -ItemType Directory $cache | Out-Null
Run 'mvn' @('-o','-q','-f',(Join-Path $Repo 'weasis-dicom-tools'),'dependency:copy-dependencies',
            '-DincludeGroupIds=org.weasis.thirdparty.org.opencv','-DincludeArtifactIds=opencv_java',
            "-DincludeClassifiers=$Cls","-DoutputDirectory=$cache")
$dll = Get-ChildItem "$cache\opencv_java-*-$Cls.dll" | Select-Object -First 1
if (-not $dll) { throw "no opencv_java-*-$Cls.dll in $cache (build the project so it is cached in ~/.m2)" }
Copy-Item -Force $dll.FullName "$Dist\lib\$Cls\opencv_java.dll"
Write-Host "== Local smoke on $Cls : $($dll.Name)"

# 3) Run the smoke (swap byte-order check, then threaded decode), on the host JVM.
$cp = "$Dist\classes;$Dist\jars\*"; $libpath = "$Dist\lib\$Cls"; $rc = 0
if (Test-Path "$Dist\data\$BeName") {
  & java "-Dexpected.be.hash=$BeHash" "-Djava.library.path=$libpath" -cp $cp SmokeTest swap "$Dist\data\$BeName"
  if ($LASTEXITCODE -ne 0) { $rc = $LASTEXITCODE }
}
if ($rc -eq 0) {
  & java "-Djava.library.path=$libpath" -cp $cp SmokeTest threads "$Dist\data\$Sample"
  if ($LASTEXITCODE -ne 0) { $rc = $LASTEXITCODE }
}
if ($rc -eq 0) { Write-Host "[PASS] $Cls (local smoke)" -ForegroundColor Green }
else { Write-Host "[FAIL] $Cls (local smoke) - exit $rc" -ForegroundColor Red }
exit $rc