param([string]$Log = "evidence/video-s3.log")
$l = Get-Content $Log | Where-Object { $_ -match '^\d\d:\d\d:\d\d\.\d\d\d ' }
"first v2: " + ($l | Select-String '"version":"v2"' | Select-Object -First 1).Line.Substring(0,12)
"last  v1: " + ($l | Select-String '"version":"v1"' | Select-Object -Last 1).Line.Substring(0,12)