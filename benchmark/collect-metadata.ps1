# SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
#
# Windows counterpart of collect-metadata.sh: emit the benchmark build-metadata sidecar
# JSON for a local Windows run, so results carry the same provenance as CI.
#
#   powershell -NoProfile -File collect-metadata.ps1 -Classifier <id> -Out <out.json>
param(
  [Parameter(Mandatory = $true)][string]$Classifier,
  [Parameter(Mandatory = $true)][string]$Out
)
$ErrorActionPreference = 'Stop'

$cpu = Get-CimInstance Win32_Processor | Select-Object -First 1
$gitSha = (git rev-parse HEAD 2>$null); if (-not $gitSha) { $gitSha = '' }
$gitRef = (git rev-parse --abbrev-ref HEAD 2>$null); if (-not $gitRef) { $gitRef = '' }

[ordered]@{
  classifier    = $Classifier
  runner_label  = "local:$env:COMPUTERNAME"
  runner_os     = 'Windows'
  runner_arch   = $env:PROCESSOR_ARCHITECTURE
  emulated      = $false
  image_os      = ''
  image_version = ''
  cpu_model     = ($cpu.Name).Trim()
  cpu_count     = "$($cpu.NumberOfLogicalProcessors)"
  git_sha       = $gitSha
  git_ref       = $gitRef
  run_url       = ''
} | ConvertTo-Json | Set-Content -Encoding utf8 $Out