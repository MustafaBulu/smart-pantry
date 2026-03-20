# Smart Pantry

## Project Description
Smart Pantry is a Spring Boot service that tracks product prices from marketplace sources and stores daily price history for later search and analysis.

## Project Goal
Build a reliable backend that centralizes category-based product tracking and automates daily marketplace price collection.

## Tech Stack
- Java 21
- Spring Boot (WebMVC, Data JPA, Validation)
- PostgreSQL
- Maven
- OkHttp, Jsoup, Selenium
- Jackson XML
- Springdoc OpenAPI
- Spring Security (JWT)
- Actuator + Prometheus metrics

## Key Features
- Category management API
- Add marketplace products by external ID and category
- Marketplace scraping for product details (Migros, Yemeksepeti)
- Daily price history recording with a scheduler
- Product search by marketplace and/or category

## Project Structure
```
src/main/java/com/mustafabulu/smartpantry
  common/        Shared layers (config, controller, dto, core, repository, service, security)
  migros/        Migros domain (service, model)
  yemeksepeti/   Yemeksepeti domain (service, model)
frontend/
  src/           Next.js frontend app
src/main/resources
  application.properties
```

## Setup Steps
1. Install Java 21 and PostgreSQL.
2. Create a database and user for the app.
3. Set the required environment variables (example from `.env`):
   - `DB_URL`
   - `DB_USERNAME`
   - `DB_PASSWORD`
   - `YEMEKSEPETI_BASE_URL`
   - `MIGROS_PREFIX_URL`
   - `MIGROS_SUFFIX_URL`
4. Run the app:
   - `./mvnw spring-boot:run`

### Security Configuration
- `SECURITY_JWT_ENABLED=true` to enforce JWT auth for non-GET endpoints.
- `SECURITY_JWT_SECRET` set a strong random secret (32+ chars).
- `SECURITY_JWT_ADMIN_USERNAME` and `SECURITY_JWT_ADMIN_PASSWORD` configure admin login.
- `SETTINGS_API_KEY` protects sensitive settings endpoints:
  - `POST /settings/daily-details/trigger`
  - `POST /settings/migros/cookies/refresh`
  - `GET /settings/migros/cookies/status`

Issue a JWT token:
```bash
curl -X POST http://localhost:8080/auth/token \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"admin\",\"password\":\"<SECURITY_JWT_ADMIN_PASSWORD>\"}"
```

Use settings API key:
```bash
curl -X POST "http://localhost:8080/settings/daily-details/trigger?marketplaceCode=MG" \
  -H "X-Settings-Api-Key: <SETTINGS_API_KEY>"
```

## Docker Setup
Run all services (PostgreSQL + discovery + 2 marketplace services + API gateway + frontend):

```bash
docker compose up --build
```

Services:
- Frontend: `http://localhost:3000`
- API Gateway: `http://localhost:8080`
- Migros service (direct): `http://localhost:8081`
- Yemeksepeti service (direct): `http://localhost:8082`
- Discovery Server: `http://localhost:8761`
- PostgreSQL: `localhost:5432`

Stop services:

```bash
docker compose down
```

Stop services and remove DB volume:

```bash
docker compose down -v
```

### Development Mode (Hot Reload)
Use the dev compose file to run microservices + frontend in development mode:

```bash
docker compose -f docker-compose.dev.yml up --build
```

Services:
- Frontend (Next.js dev): `http://localhost:3000`
- API Gateway: `http://localhost:8080`
- Migros service (Spring Boot run): `http://localhost:8081`
- Yemeksepeti service (Spring Boot run): `http://localhost:8082`
- Discovery Server: `http://localhost:8761`
- PostgreSQL: `localhost:5432`

Frontend gateway-first mode:
- Set `NEXT_PUBLIC_GATEWAY_ONLY=true` to disable direct fallback calls to `:8081/:8082`.
- Recommended when running behind API gateway (`http://localhost:8080`).

Stop:

```bash
docker compose -f docker-compose.dev.yml down
```

Stop + remove dev DB volume:

```bash
docker compose -f docker-compose.dev.yml down -v
```

## Microservices Mode (Migros + Yemeksepeti)
Marketplace logic can run as 2 separate backend services:
- `migros-service` internal `:8080` (profile: `mg`, DB: `smart_pantry_mg`)
- `yemeksepeti-service` internal `:8080` (profile: `ys`, DB: `smart_pantry_ys`)
- `api-gateway` on `http://localhost:8080`
  - Migros routes: `http://localhost:8080/mg/...`
  - Yemeksepeti routes: `http://localhost:8080/ys/...`

Start both marketplace microservices:
```bash
docker compose -f docker-compose.microservices.yml up --build
```

Start both marketplace microservices in development mode (hot reload):
```bash
docker compose -f docker-compose.microservices.dev.yml up --build
```

Stop them:
```bash
docker compose -f docker-compose.microservices.yml down
```

Stop development mode:
```bash
docker compose -f docker-compose.microservices.dev.yml down
```

Local run without Docker:
```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=mg
./mvnw spring-boot:run -Dspring-boot.run.profiles=ys
```

Build separate backend artifacts:
```bash
./mvnw -Pmg-service -DskipTests package
./mvnw -Pys-service -DskipTests package
```
Generated jars:
- `target/smart-pantry-mg-service.jar`
- `target/smart-pantry-ys-service.jar`

## Usage
Base URL: `http://localhost:8080`

Create a category:
```bash
curl -X POST http://localhost:8080/categories \
  -H "Content-Type: application/json" \
  -d "{\"name\":\"Snacks\"}"
```

Add a product to a marketplace category:
```bash
curl -X POST http://localhost:8080/marketplaces/YS/categories/Snacks/addproduct \
  -H "Content-Type: application/json" \
  -d "{\"productId\":\"12345\"}"
```

Search products:
```bash
curl "http://localhost:8080/marketplaces/products?marketplaceCode=YS&categoryName=Snacks"
```

## Observability
- Health: `GET /actuator/health`
- Info: `GET /actuator/info`
- Prometheus metrics: `GET /actuator/prometheus`

## CI
- GitHub Actions workflow: `.github/workflows/ci.yml`
- Backend: Maven tests
- Frontend: lint + typecheck + build
- SonarCloud: backend+frontend CI sonrası çalışır (secret varsa).

SonarCloud için GitHub repo ayarları:
- `SONAR_TOKEN` (Actions Secret) zorunlu
- `SONAR_ORGANIZATION` (Actions Variable) opsiyonel, default: repo owner
- `SONAR_PROJECT_KEY` (Actions Variable) opsiyonel, default: `<owner>_<repo>`
