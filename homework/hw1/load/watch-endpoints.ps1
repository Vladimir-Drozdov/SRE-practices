param([string]$Out = "evidence/video-endpoints.log")
Remove-Item $Out -ErrorAction SilentlyContinue
while ($true) {
  $s = kubectl get endpointslices -l kubernetes.io/service-name=hello-app -o jsonpath="{range .items[0].endpoints[*]}{.targetRef.name}={.conditions.ready} {end}"
  $s = $s -replace 'hello-app-[0-9a-f]+-', ''
  "$(Get-Date -Format HH:mm:ss.fff) $s" | Tee-Object -FilePath $Out -Append
  Start-Sleep -Milliseconds 700
}