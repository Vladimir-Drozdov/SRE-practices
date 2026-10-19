# ДЗ 1. Доступность приложения в Kubernetes

Скринкаст (4:57): https://drive.google.com/file/d/14lxxAzdcAoCQSEdydaIQ-V_aujbXF1-0/view?usp=sharing

| Время | Раздел |
| --- | --- |
| 0:00 | Карта ролика |
| 0:12 | Манифесты |
| 0:57 | Запуск |
| 1:20 | Сценарий 1 |
| 2:08 | Сценарий 2 |
| 2:56 | Сценарий 3 |
| 4:02 | Выводы |

Путь `C:\SRE_systems\HW1_K8s` в консоли на видео - это та же папка, которую я потом переименовал в `homework\hw1`.

## Схема

```
клиент (load.ps1, внутри него curl.exe), Windows
   │  http://localhost:30080/
   ▼
контейнер узла kind (hw1-control-plane), порт 30080 проброшен через extraPortMappings
   ▼
Service hello-app: NodePort 30080 → port 80 → targetPort 8080
   │  берёт адреса только Ready-Pod из EndpointSlice
   ├───────────┬───────────┐
   ▼           ▼           ▼
 Pod 1       Pod 2       Pod 3      (метка app=hello-app)
   ▲
Deployment → ReplicaSet → Pod
```

- Приложение (Java, Spring Boot): `GET /` отдаёт message, version и pod; `/live` всегда 200; `/ready` отвечает 200 или 503 по внутреннему флагу; `POST /unready` и `POST /ready-on` переключают флаг. Версия задаётся при сборке образа, имя Pod берётся из hostname. На SIGTERM приложение доделывает текущие запросы (`server.shutdown=graceful`).
- Deployment держит 3 Pod, Service раздаёт запросы только на Ready-Pod из EndpointSlice.
- Путь запроса: клиент → localhost:30080 → узел kind → Service → один из Pod → порт 8080. `kubectl port-forward` не использовал.

## Сборка и запуск с чистого кластера

Из папки `homework/hw1`, Docker Desktop уже запущен:

```powershell
kind delete cluster --name hw1          # если кластер уже был

docker build --build-arg VERSION=v1 -t hello-app:v1 app
docker build --build-arg VERSION=v2 -t hello-app:v2 app

kind create cluster --name hw1 --config kubernetes/kind-config.yaml
kind load docker-image hello-app:v1 --name hw1
kind load docker-image hello-app:v2 --name hw1
kubectl apply -f kubernetes/deployment.yaml -f kubernetes/service.yaml
kubectl rollout status deployment/hello-app
```

Проверка (то же показано в видео в разделе «Запуск»):

```powershell
kubectl get pods -o wide
kubectl get endpointslices -l kubernetes.io/service-name=hello-app
1..5 | ForEach-Object { curl.exe -s http://localhost:30080/; "" }
```

Должно быть три Pod `1/1 Running`, три адреса в EndpointSlice и ответы с `"version":"v1"`.

Базовый замер до сценариев (`evidence/baseline.log`): 600 запросов за ~20 с, ошибок 0, все ответы v1, нагрузка примерно поровну по трём Pod (213 / 195 / 192).

В `kind-config.yaml` кроме проброса порта 30080 зафиксирован порт API (16443). Случайный порт один раз попал в зарезервированный Windows диапазон, и Docker перестал открывать порты кластера.

Скрипты в `load/` запускаю так: `Set-ExecutionPolicy -Scope Process Bypass -Force`, затем `.\load\load.ps1 ...`. После сценария 3 возвращаю v1 командой `.\load\reset-v1.ps1`.

## Версии

- Windows 11 Home 10.0.26200
- Docker 29.8.1 (Docker Desktop 4.93.0)
- kubectl v1.36.1
- kind v0.33.0, узел kindest/node:v1.37.0 (Kubernetes v1.37.0)
- Java 21, Spring Boot 3.3.5 (сборка идёт внутри Docker)

## Сценарий 1. Удаление Pod

Команды (три окна):

```powershell
.\load\watch-ready.ps1 -Out evidence/video-s1-ready.log
.\load\load.ps1 -Count 1000 -Out evidence/video-s1.log
.\load\s1-delete-pod.ps1
```

`watch-ready.ps1` пишет число Ready-реплик. `load.ps1` создаёт нагрузку через Service: запросы идут по очереди, каждый новым `curl.exe`, таймаут 1 с. В конце он печатает итог. `s1-delete-pod.ps1` удаляет один Pod.

Замеры (`evidence/video-s1.log`): 1000 запросов за 27.1 с (~36.9 запросов/с). Ошибок нет: ни таймаутов, ни отказов соединения, ни ошибок. Все ответы v1.

По Pod запросы распределились так: 449 / 417 / 100 / 34. Имён четыре, потому что к трём исходным Pod добавилась замена. Удалённый Pod ответил до удаления, а замена - после того, как стала Ready.

Как это выглядело по числу Ready-реплик (`evidence/video-s1-ready.log`):

- до 15:07:47 - Ready-реплик три
- 15:07:48 - Pod удалили, Ready-реплик стало две (Deployment сразу создаёт замену)
- 15:08:03 - замена запустилась и готова принимать запросы (статус 1/1), Ready-реплик снова три

Около 15 с Ready-реплик было две, потом снова три.

Выводы:
- Пока удаляли Pod, ни один запрос не упал. Удаляемый Pod перестаёт получать трафик и спокойно доделывает текущие запросы, а две другие реплики работают без перерыва. Для этого настроены preStop (5 с) и graceful shutdown
- Замена стала Ready только через ~15 с, пока стартует Java. Всё это время сервис работал на 2/3 мощности, и каждая оставшаяся реплика получала в полтора раза больше запросов.

## Сценарий 2. Pod в not-ready

Команды:

```powershell
.\load\watch-endpoints.ps1 -Out evidence/video-s2-endpoints.log
.\load\load.ps1 -Count 1100 -Out evidence/video-s2.log
.\load\s2-not-ready.ps1
```

`s2-not-ready.ps1` отправляет `/unready` одному Pod через `kubectl exec`. Через Service это делать нельзя, потому что флаг свой у каждого Pod. Через 15 с скрипт отправляет `/ready-on`. `watch-endpoints.ps1` пишет флаг ready каждого Pod в EndpointSlice.

Замеры (`evidence/video-s2.log`): 1100 запросов за 30.7 с (~35.8 запросов/с), ошибок 0, все ответы v1. По Pod: 463 / 459 / 178. Pod со 178 запросами - тот, что был выведен из трафика, поэтому он получил заметно меньше остальных.

Подробности из лога (`evidence/video-s2-endpoints.log`):
- в `kubectl get endpointslices` адрес Pod остаётся в списке, меняется только его условие ready. Поэтому флаг записан отдельно: у Pod `bvxbv` ready=false с 15:09:26.983 до 15:09:39.831 (~13 с), в 15:09:40.623 снова true. У двух других Pod (`dlnqk`, `pg4tv`) true весь период;
- пока Pod был not-ready, остальные два отвечали без ошибок (во всём прогоне ошибок 0).

Параметры проб и почему такие:
- readiness: `/ready`, период 2 с, таймаут 1 с, 2 неудачи подряд - Pod выпадает из трафика за 2-4 с. Исключение из Service дешёвое (Pod не перезапускается), поэтому проба частая.
- liveness: `/live`, период 10 с, 3 неудачи, initialDelay 20 с - перезапуск дорогой (старт JVM занимает примерно 30с), поэтому проба реже и мягче. Она проверяет только, что процесс жив.

Выводы:
- Pod, у которого `/ready` стал отвечать 503, перестал получать запросы. Это видно по 178 запросам против ~460 у остальных.
- `/live` при этом продолжал отвечать 200, поэтому Kubernetes контейнер не перезапускал (RESTARTS = 0).
- Из трафика Pod выпал не сразу. После `/unready` он ещё 2-4 с получал запросы, пока readiness-проба не получила 503 два раза подряд.

## Сценарий 3. Обновление v1 → v2

Команды:

```powershell
.\load\watch-ready.ps1 -Out evidence/video-s3-ready.log
.\load\load.ps1 -Count 2400 -Out evidence/video-s3.log
.\load\s3-update.ps1
.\load\s3-switch.ps1
kubectl get rs
```

`s3-update.ps1` выполняет `kubectl set image deployment/hello-app hello-app=hello-app:v2` и ждёт `rollout status`. `s3-switch.ps1` печатает время первого ответа v2 и последнего ответа v1.

Замеры (`evidence/video-s3.log`): 2400 запросов за 61.9 с (~38.8 запросов/с), ошибок 0. Версии: v1 1242, v2 1158. По Pod: v1 - 587 / 420 / 235, v2 - 567 / 370 / 221.

Первый ответ v2 пришёл в 15:11:02.727, последний v1 - в 15:11:31.783. Значит, около 29 с отвечали обе версии. `kubectl get rs` после обновления: старый ReplicaSet 0/0/0, новый 3/3/3.

Заменяются все три Pod, по одному: новый Pod запускается, и только когда он стал Ready, старый уходит в Terminating.

Поставил `maxUnavailable: 0` и `maxSurge: 1`, это означает, что готовых Pod всё время не меньше трёх, а всего временно четыре. Цена такого выбора - нужен запас ресурсов на четвёртый Pod, и обновление идёт по одному Pod, то есть дольше.

Выводы:
- Все 2400 запросов получили 200: ни таймаутов, ни отказов соединения, ни ошибок.
- Пока шло обновление, одновременно отвечали обе версии, поэтому они должны быть совместимы между собой.

## Границы гарантий

1. Узел один (он же control plane), поэтому если он откажет, пропадут сразу все три реплики. Несколько реплик на одном узле от этого не защищают. Нужны несколько узлов и реплики на разных узлах.
2. Если новая версия не стартует, rollout встанет, а старые Pod продолжат отвечать (`maxUnavailable: 0`). Но если ошибка есть в обеих версиях или `/ready` сразу на всех Pod начнёт отвечать 503, Kubernetes не поможет: у Service не останется endpoints.
3. Пробы смотрят только на ответы `/live` и `/ready`, а не на то, правильно ли работает приложение. У меня `/ready` зависит от флага, а `GET /` этот флаг не проверяет. Поэтому Pod ещё несколько секунд получал запросы после `/unready`, пока проба не сработала.
4. PodDisruptionBudget защищает только от eviction, а `kubectl delete pod` его обходит. PDB в работе не создавал. Удаление в сценарии 1 прошло без ошибок не из-за защиты: Deployment пересоздаёт Pod, а остальные реплики продолжают отвечать.
5. `terminationGracePeriodSeconds` - 30 с, это лимит на остановку: `preStop` занимает 5 с, остановка Spring до 20 с. Если приложение не успеет, Kubernetes убьёт его через SIGKILL, и запросы в работе оборвутся. `preStop` нужен, чтобы адрес успел исчезнуть из Service до SIGTERM.