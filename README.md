# Sentiment API

Учебный сервис анализа тональности текста. Ответ строится по словарю, без ML-модели: так образ остаётся меньше 150 МБ, а контракт эндпоинта совпадает с заданием.

`GET /api/sentiment?text=hello` → `{"sentiment":"positive"}`

Отрицательное слово перекрывает положительное. Незнакомый текст получает метку `neutral`.

## Стек

- Java 21 (bytecode), Spring Boot 4.1
- Actuator: `/actuator/health/liveness`, `/actuator/health/readiness`, `/actuator/prometheus`
- Docker-образ с урезанным runtime (`jlink`), не с полным JRE
- Kubernetes: Deployment на 3 реплики, Service `LoadBalancer`, Ingress `/api`, HPA по CPU
- Prometheus и Grafana через `kube-prometheus-stack`

## Локальная проверка

```bash
./gradlew test bootRun
```

Во втором терминале:

```bash
curl "http://localhost:8080/api/sentiment?text=hello"
curl "http://localhost:8080/api/sentiment?text=terrible"
curl "http://localhost:8080/api/sentiment?text=table"
curl -s http://localhost:8080/actuator/prometheus | head
```

## Образ

Полный тег `eclipse-temurin:21-jre-alpine` занимает около 200 МБ. В `Dockerfile` runtime собирается через `jlink`, в финальный слой попадает только он и загрузчик musl.

```bash
docker build --provenance=false --sbom=false -t sentiment-api:1.0 .
docker images sentiment-api:1.0
docker run --rm -d -p 8080:8080 --name sentiment sentiment-api:1.0
curl "http://localhost:8080/api/sentiment?text=hello"
docker stop sentiment
```

В Docker Desktop 29 колонка называется Disk Usage. У собранного образа она около 144MB, Content Size около 61MB. Оба числа ниже лимита 150 МБ. Флаги `--provenance=false --sbom=false` отключают attestation-манифесты, которые в этой версии Docker увеличивают Disk Usage. Полный тег `eclipse-temurin:21-jre-alpine` сам по себе около 200 МБ, поэтому в образ попадает только runtime, собранный `jlink`, и загрузчик musl.

Кластер Minikube со своим containerd не видит образы Docker Desktop, пока образ не загружен:

```bash
minikube image load sentiment-api:1.0
minikube image ls | grep sentiment
```

После пересборки того же тега образ в кластере нужно загрузить снова и перезапустить поды:

```bash
minikube image load sentiment-api:1.0
kubectl rollout restart deployment/sentiment-api
```

Платформу `linux/amd64` указывать не нужно: и Mac, и кластер в этой работе — `arm64`.

## Кластер

Docker Desktop перед стартом: не меньше 18 ГБ памяти и 8 CPU. Иначе два узла по 8 ГБ не поднимутся.

```bash
minikube start --cpus=4 --memory=8192mb --nodes=2
minikube addons enable ingress
minikube addons enable metrics-server
kubectl apply -f k8s/deployment.yaml -f k8s/service.yaml -f k8s/ingress.yaml -f k8s/hpa.yaml
kubectl rollout status deployment/sentiment-api
kubectl get deploy,svc,ingress,hpa,pods -o wide
```

`LoadBalancer` получает EXTERNAL-IP только пока во втором терминале работает туннель:

```bash
minikube tunnel
```

```bash
curl "http://127.0.0.1/api/sentiment?text=hello"
curl "http://$(minikube ip)/api/sentiment?text=hello"
```

HPA начинает показывать процент CPU после того, как заработает metrics-server. Пока в столбце `TARGETS` стоит `<unknown>`, снимок рано делать.

```bash
kubectl top pods
kubectl describe hpa sentiment-api
```

Нагрузка на три минуты, шесть параллельных клиентов:

```bash
kubectl delete job sentiment-load --ignore-not-found
kubectl apply -f k8s/load/job.yaml
kubectl get hpa sentiment-api -w
```

Запрос CPU у пода — 50m, порог HPA — 50%. Цикл в `SentimentAnalyzer` задаёт фиксированную стоимость разбора, иначе словарный mock почти не грузит процессор и реплики не растут. Метка тональности от этого цикла не зависит.

## Мониторинг

```bash
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo update
kubectl create namespace monitoring
helm install prometheus prometheus-community/kube-prometheus-stack \
  --namespace monitoring \
  -f k8s/monitoring/values.yaml
kubectl get pods -n monitoring
```

Метка `release: prometheus` на ServiceMonitor должна совпасть с селектором Prometheus. Проверка:

```bash
kubectl get prometheus -n monitoring -o jsonpath='{.items[0].spec.serviceMonitorSelector}'; echo
kubectl apply -f k8s/monitoring/servicemonitor.yaml
```

Имена Service смотреть у себя, у chart они зависят от версии:

```bash
kubectl get svc -n monitoring
kubectl port-forward -n monitoring svc/prometheus-kube-prometheus-prometheus 9090:9090
kubectl port-forward -n monitoring svc/prometheus-grafana 3000:80
```

- Prometheus: <http://localhost:9090/targets> — job ServiceMonitor в состоянии UP
- Grafana: <http://localhost:3000>, логин `admin`, пароль `Sentiment-Lab-2026`
- Дашборд: Dashboards → Import → файл `k8s/monitoring/grafana-dashboard.json`, источник Prometheus

Дашборд фильтрует метрики по `application="sentiment-api"`. Панель разбора тональности растет от запросов на `/api/sentiment`.

Пароль Grafana учебный, в этом репозитории других секретов нет.
