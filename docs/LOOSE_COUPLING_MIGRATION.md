# Loose Coupling Migration Plan

## Goal
Decouple shared code from runtime service responsibilities and move toward a gateway-first architecture.

## Phase 1 (Started): Gateway-First Client Access
- Status: in progress
- Change applied:
  - `frontend/src/lib/api.ts`
  - New env: `NEXT_PUBLIC_GATEWAY_ONLY=true|false`
  - When `true`, frontend does not fall back to direct service ports (`:8081`, `:8082`).

## Phase 2: Move Runtime Layers Out of `smart-pantry-common`
- Status: pending
- Keep in `smart-pantry-common`:
  - `common/dto/**`
  - `common/enums/**`
  - `common/core/**` (exceptions, response models, neutral utilities)
  - service contracts/interfaces only:
    - `common/service/MarketplaceProductConnector.java`
    - `common/service/MarketplaceCategoryFetchService.java`
- Move from `smart-pantry-common` to service apps:
  - `common/controller/**`
  - `common/repository/**`
  - `common/model/**`
  - `common/scheduler/**`
  - `common/config/SecurityConfig.java`
  - `common/security/**`

## Phase 3: Security Boundary
- Status: pending
- Gateway:
  - JWT verification
  - CORS policy
  - rate limiting
- Services:
  - endpoint-level authorization for sensitive actions
  - keep `SettingsAccessGuard`-style checks for operational endpoints

## Phase 4: Communication Style (Sync vs Async)
- Status: pending
- Sync:
  - read-heavy APIs (`GET /products`, `GET /categories`, `GET /history`)
  - short command operations that need immediate response
- Async:
  - product detail refresh after add
  - daily snapshot jobs
  - bulk import/refresh
- Candidate files to refactor first:
  - `smart-pantry-common/src/main/java/com/mustafabulu/smartpantry/common/service/MarketplaceProductService.java`
  - `smart-pantry-common/src/main/java/com/mustafabulu/smartpantry/common/service/DailyProductDetailsTriggerService.java`
  - `smart-pantry-common/src/main/java/com/mustafabulu/smartpantry/common/scheduler/DailyProductDetailsScheduler.java`

## Phase 5: Service-Owned Data
- Status: pending
- Each service owns its schema and migration history.
- Remove cross-service direct table ownership assumptions.

## Execution Order
1. Run frontend in gateway-only mode and validate all screens.
2. Extract runtime web/security/data layers from `smart-pantry-common`.
3. Introduce async job model for detail refresh and scheduled collection.
4. Add gateway-level auth/rate-limit and keep service-level guards.
5. Finalize service-owned schema boundaries.

