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

## Key Features
- Category management API
- Add marketplace products by external ID and category
- Marketplace scraping for product details (Migros, Yemeksepeti)
- Daily price history recording with a scheduler
- Product search by marketplace and/or category

## Project Structure
```
src/main/java/com/mustafabulu/smartpantry
  config/        Configuration properties
  controller/    REST controllers
  core/          Exceptions, logging, utilities, responses
  dto/           Request/response DTOs
  enums/         Marketplace enum
  migros/        Migros scraper and models
  model/         JPA entities
  repository/    Spring Data repositories
  scheduler/     Scheduled jobs
  service/       Business services
  yemeksepeti/   Yemeksepeti scraper and models
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

## Docker Setup
Run all services (PostgreSQL + backend + frontend):

```bash
docker compose up --build
```

Services:
- Frontend: `http://localhost:3000`
- Backend API: `http://localhost:8081`
- PostgreSQL: `localhost:5432`

Stop services:

```bash
docker compose down
```

Stop services and remove DB volume:

```bash
docker compose down -v
```

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
