param([string]$Out = "evidence/video-ready.log")
Remove-Item $Out -ErrorAction SilentlyContinue
while ($true) {
  $s = kubectl get deployment hello-app -o jsonpath="{.status.readyReplicas} {.status.replicas}"
  $p = $s -split " "
  if (-not $p[0]) { $p[0] = 0 }
  "$(Get-Date -Format HH:mm:ss.fff) ready: $($p[0])  pods total: $($p[1])" | Tee-Object -FilePath $Out -Append
  Start-Sleep -Milliseconds 700
}
