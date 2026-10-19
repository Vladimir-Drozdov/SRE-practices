$pod = kubectl get pods -o name | Select-Object -First 1
"$(Get-Date -Format HH:mm:ss.fff) deleting $pod"
kubectl delete $pod