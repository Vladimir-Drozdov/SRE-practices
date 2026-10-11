param([int]$Hold = 15)
$pod = kubectl get pods -o name | Select-Object -First 1
"$(Get-Date -Format HH:mm:ss.fff) /unready -> $pod"
kubectl exec $pod -- curl -s -w '\n' -X POST http://localhost:8080/unready
Start-Sleep 5
"$(Get-Date -Format HH:mm:ss.fff) GET /ready (probe endpoint) ->"
kubectl exec $pod -- curl -s -o /dev/null -w 'HTTP %{http_code}\n' http://localhost:8080/ready
"$(Get-Date -Format HH:mm:ss.fff) GET /live ->"
kubectl exec $pod -- curl -s -o /dev/null -w 'HTTP %{http_code}\n' http://localhost:8080/live
Start-Sleep ($Hold - 5)
"$(Get-Date -Format HH:mm:ss.fff) /ready-on -> $pod"
kubectl exec $pod -- curl -s -w '\n' -X POST http://localhost:8080/ready-on
Start-Sleep 5
kubectl get pods
kubectl describe $pod | Select-String "Readiness probe failed" | Select-Object -Last 1