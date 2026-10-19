# ДЗ 2. Runtime feature flag `new-greeting`

## Что сделано

В приложение из первой работы добавлен флаг `new-greeting`. Он хранится в памяти процесса и по умолчанию выключен.

- `GET /message`: при выключенном флаге возвращает `hello`, при включенном `Welcome! This is the new greeting`.
- `GET /admin/feature?enabled=true` включает флаг, `GET /admin/feature?enabled=false` выключает.
- `GET /admin/feature` без параметра показывает текущее состояние флага.
- Другое значение `enabled` дает 400, флаг не меняется.

## Тесты и CI

Тесты (`app/src/test/java/demo/FeatureFlagTest.java`) проверяют: флаг выключен по умолчанию, включение меняет ответ `/message`, выключение возвращает старый ответ, некорректное значение дает 400 и не меняет флаг.

```powershell
cd homework\hw2\app
mvn -B test
```

Результат: `Tests run: 6, Failures: 0, Errors: 0`, `BUILD SUCCESS`.

CI (`.github/workflows/ci.yml`) запускается на push и pull request: выполняет `mvn -B test` и собирает Docker-образ с тегом, равным SHA коммита.

Результат CI: https://github.com/Vladimir-Drozdov/SRE-practices/actions/runs/39321823049/job/118297521755

Скриншот: `evidence/ci.png`

## Команды выкладки

Из папки `homework/hw2`:

```powershell
docker build --build-arg VERSION=flag-v1 -t hello-app:flag-v1 app
kind create cluster --name hw2 --config kubernetes/kind-config.yaml
kind load docker-image hello-app:flag-v1 --name hw2
kubectl apply -f kubernetes/deployment.yaml -f kubernetes/service.yaml
kubectl rollout status deployment/hello-app
```

## Проверка переключения

```
PS> kubectl get pods -o custom-columns=NAME:.metadata.name,UID:.metadata.uid,RESTARTS:.status.containerStatuses[0].restartCount
NAME                         UID                                    RESTARTS
hello-app-5748fcfbd5-z47z4   54f3cb60-d5ca-4ad7-8db8-d71dbae9c6b4   0

PS> curl.exe -s http://localhost:30080/admin/feature
{"feature":"new-greeting","pod":"hello-app-5748fcfbd5-z47z4","enabled":false}

PS> curl.exe -s http://localhost:30080/message
{"newGreeting":false,"version":"flag-v1","message":"hello","pod":"hello-app-5748fcfbd5-z47z4"}

PS> curl.exe -s "http://localhost:30080/admin/feature?enabled=true"
{"feature":"new-greeting","pod":"hello-app-5748fcfbd5-z47z4","enabled":true}

PS> curl.exe -s http://localhost:30080/message
{"newGreeting":true,"version":"flag-v1","message":"Welcome! This is the new greeting","pod":"hello-app-5748fcfbd5-z47z4"}

PS> curl.exe -s "http://localhost:30080/admin/feature?enabled=false"
{"feature":"new-greeting","pod":"hello-app-5748fcfbd5-z47z4","enabled":false}

PS> curl.exe -s http://localhost:30080/message
{"newGreeting":false,"version":"flag-v1","message":"hello","pod":"hello-app-5748fcfbd5-z47z4"}

PS> kubectl get pods -o custom-columns=NAME:.metadata.name,UID:.metadata.uid,RESTARTS:.status.containerStatuses[0].restartCount
NAME                         UID                                    RESTARTS
hello-app-5748fcfbd5-z47z4   54f3cb60-d5ca-4ad7-8db8-d71dbae9c6b4   0
```

Следующий запрос после каждого переключения сразу показывает новое состояние. Имя Pod, UID и `RESTARTS = 0` до и после совпадают, значит, Pod не перезапускался и образ не менялся.

Лог: `evidence/feature-demo.log`.

## Вывод: флаг и выкладка новой версии

Выкладка новой версии означает, что мы собираем новый образ и заменяем им работающие Pod. Это занимает время, Pod перезапускаются, и если что-то пошло не так, нужно выкатывать предыдущую версию обратно.
Feature flag это переключатель внутри уже работающего приложения. Код с новым приветствием мы уже выложили заранее, но он был выключен. Чтобы включить его, достаточно одного запроса /admin/feature?enabled=true: образ не пересобирается, Pod не перезапускается. В работе это видно по тому, что имя Pod, его UID и RESTARTS = 0 до и после переключения одинаковые.
Разница в том, что выкладка меняет программу, а флаг меняет поведение уже работающей программы. Поэтому новую функцию можно сначала спокойно выложить выключенной, а включить позже, когда это нужно. Если новое поведение вызвало проблемы, его достаточно выключить тем же запросом, не откатывая версию.
## Ограничения

- Флаг хранится в памяти одного Pod, поэтому использована 1 реплика: при нескольких репликах запросы через Service попадали бы в Pod с разным состоянием. После перезапуска Pod флаг сбрасывается в выключенное состояние.
- Админ-эндпоинт не защищен и меняет состояние через GET. Он подходит только для локального кластера: NodePort слушает только `127.0.0.1`, в интернет эндпоинт не выставляется.