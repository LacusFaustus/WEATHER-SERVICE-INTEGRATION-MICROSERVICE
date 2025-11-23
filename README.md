# Weather Service Integration Microservice

Высокодоступный микросервис для агрегации данных о погоде с поддержкой multiple провайдеров.

## 🚀 Особенности

- **Reactive Architecture** - Spring WebFlux, Java 21
- **Multiple Providers** - OpenWeatherMap, WeatherAPI, AccuWeather
- **Intelligent Caching** - Redis + In-memory fallback
- **Resilience Patterns** - Circuit Breaker, Retry, Rate Limiting
- **Monitoring** - Micrometer, Health checks, Prometheus metrics
- **Graceful Degradation** - Automatic fallback between providers

## 📋 Требования

- Java 21+
- Maven 3.6+
- Redis 7+ (опционально)

## 🏃 Быстрый старт

```bash
# Клонирование репозитория
git clone https://github.com/LacusFaustus/WEATHER-SERVICE-INTEGRATION-MICROSERVICE.git
cd WEATHER-SERVICE-INTEGRATION-MICROSERVICE

# Запуск
mvn spring-boot:run

# Или с Docker
docker-compose up -d
