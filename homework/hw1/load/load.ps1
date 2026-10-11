param(
  [string]$Url = "http://localhost:30080/",
  [int]$Count = 1500,
  [string]$Out = "evidence/run.log"
)

$dir = Split-Path $Out
if ($dir) { New-Item -ItemType Directory -Force $dir | Out-Null }

$start = Get-Date
1..$Count | ForEach-Object {
  $t = (Get-Date).ToString("HH:mm:ss.fff")
  $res = curl.exe -s -m 1 -w " %{http_code} %{exitcode}" $Url
  "$t $res"
} | Tee-Object $Out
$sec = ((Get-Date) - $start).TotalSeconds

# Итог
$log  = Get-Content $Out
$errs = $log | Where-Object { $_ -notmatch ' 200 0$' }

"`n ИТОГ"
"всего запросов: $($log.Count) за $([math]::Round($sec,1)) с (~$([math]::Round($log.Count/$sec,1)) запросов/с)"
"ошибок: $($errs.Count)"

"типы ошибок:"
$errs | ForEach-Object {
  if ($_ -match ' (\d{3}) (\d+)$') {
    if ($matches[1] -eq '000') {
      switch ($matches[2]) { '28' {'timeout'} '7' {'connection_refused'} default {"curl_exit_$($matches[2])"} }
    } else { "http_$($matches[1])" }
  } else { 'unparsed' }
} | Group-Object | ForEach-Object { "  $($_.Name): $($_.Count)" }

"по версиям:"
$log | ForEach-Object { if ($_ -match '"version":"([^"]+)"') { $matches[1] } } |
  Group-Object | ForEach-Object { "  $($_.Name): $($_.Count)" }

"по Pod:"
$log | ForEach-Object { if ($_ -match '"pod":"([^"]+)"') { $matches[1] } } |
  Group-Object | ForEach-Object { "  $($_.Name): $($_.Count)" }

if ($errs) { "строки с ошибками:"; $errs }
"сырой лог: $Out"