"$(Get-Date -Format HH:mm:ss.fff) set image v2"
kubectl set image deployment/hello-app hello-app=hello-app:v2
kubectl rollout status deployment/hello-app | ForEach-Object { "$(Get-Date -Format HH:mm:ss.fff) $_" }