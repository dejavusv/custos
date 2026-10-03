# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Custos is a server automation and task-scheduling platform. It handles DB and filesystem backups, chunked FTP/SFTP transfer, Google Drive upload, and DAG pipelines that run on Quartz schedules. A React dashboard shows live logs over WebSocket. The two parts are decoupled: `backend/` is Spring Boot 3.3 on Java 21 with Maven, and `frontend/` is React 18, Vite 5 and TypeScript.

Design docs live in the repo root and are written in Thai: `rules.md` (binding conventions), `plan.md` (phased roadmap) and `server-task-scheduler-spec.md` (functional spec). Read `rules.md` before any non-trivial change.

## Commands

Local infrastructure (Postgres 16 on 5432, Redis 7 on 6379, and a mock SFTP server on 2222 with user `sftpuser`/`sftppassword` and dir `upload`):
```bash
docker compose -f docker/docker-compose.dev.yml up -d
```

Backend (run from `backend/`; use `mvnw.cmd` on Windows cmd/PowerShell):
```bash
./mvnw spring-boot:run
```
```bash
./mvnw test
```
```bash
./mvnw test -Dtest=PipelineAndSchedulerTests
```
```bash
./mvnw test -Dtest=CustosAuthTests#testSuccessfulLogin
```
```bash
./mvnw clean package -DskipTests
```

Frontend (run from `frontend/`). The dev server runs on :5173 and proxies `/api` and `/ws` to `localhost:8080`:
```bash
npm run dev
```
```bash
npm run build
```
There is no lint or test script. `npm run build` runs `tsc`, which is the only type check.

Default login: `admin` with the password from `CUSTOS_ADMIN_PASSWORD` (ROLE_SUPER_ADMIN). `application.yml` defaults it to `AdminPassword@123` for dev only; the `prod` profile has no default, and a blank value means no admin is initialized. `DataSeeder` sets it only when `admin` does not exist, or when the row is still the unusable placeholder hash inserted by `V2__seed_default_roles_and_admin.sql`. It never resets the password afterwards, so a changed password survives restarts.

## Backend architecture

Package root is `com.custos`, with feature modules under `modules/`. Each module follows the usual `controller / service / entity / repository / dto` split. Cross-cutting pieces:
- `shared/`: `BaseEntity`, `ApiResponse<T>` (every endpoint returns `{success, data, message, timestamp}`), `GlobalExceptionHandler`, and the typed exceptions (`ResourceNotFoundException`, `BadRequestException`, `UnauthorizedException`).
- `config/`: Security, WebSocket, Quartz and AWS SES wiring.

**How a pipeline run flows** (this spans several modules):
1. Quartz fires `scheduling/job/PipelineJob` (`@DisallowConcurrentExecution`), or `PipelineController` triggers a run manually. Quartz uses a JDBC JobStore, and its `qrtz_*` tables are created by Flyway in `V4__init_pipeline_schema.sql` (`initialize-schema: never`).
2. `pipeline/engine/DagPipelineOrchestrator.executePipeline` loads the `PipelineStepNode`s and runs `DagCycleDetector`. It then walks the graph from the root node: the first node that no other node targets. After a success it follows `onSuccessNodeId`, otherwise the next unexecuted node in `stepOrder`. After a failure it follows `onFailureNodeId`; if that is absent, the pipeline fails.
3. Each node's `configOverrideJson` is parsed into a map and dispatched by `nodeType`: `DATABASE_BACKUP` goes to `BackupService`, `FILE_BACKUP` to `BackupService`, `SPLIT_TRANSFER` to `ResilientTransferService`, `EMAIL_ALERT` to `NotificationService` (SES plus the Thymeleaf templates in `resources/templates/email/`), and `GOOGLE_DRIVE_UPLOAD` to `DriveUploadFacadeService.uploadLocalFileAndRecordAudit` (Drive upload plus a Firestore audit record), and `LINE_NOTIFY` to `ExternalNotifyService.send` (calls the CCenterV2 External Notify API with a vault `EXTERNAL_NOTIFY` credential; the node config stores only `credentialId`, `taskId` and `message`, and a missing or invalid `credentialId` fails the step).
   `START` and `STOP` are control nodes that do no work. A pipeline has at most one `START` (enforced in `PipelineService.savePipeline`, and the builder requires exactly one). The orchestrator begins at the `START` node, and falls back to the first untargeted node for legacy pipelines that have none. `STOP` ends the run; reaching it through an On Failure branch marks the execution `FAILED`. When a step fails, the context publishes `<nodeKey>.error_message`, `last_error_message` and `last_failed_step`. An `EMAIL_ALERT` on the failure branch sends the failure alert instead of the success summary.
4. `PipelineExecutionContext` passes data between steps. Each step publishes `<nodeKey>.output_path|checksum|file_size_bytes` and `last_output_path` etc. Later steps reference them as `${...}` placeholders through `resolvePlaceholders`. A `SPLIT_TRANSFER` without a source path uses `last_output_path`.
5. `ExecutionRegistry` tracks in-flight runs for abort. `console/ExecutionProgressBroadcaster` pushes to STOMP topics `/topic/pipeline/{executionId}/logs` and `/progress`. The endpoint is `/ws` with SockJS. The frontend `hooks/usePipelineWebSocket.ts` consumes these topics.
6. Results are stored in `pipeline_executions` and `step_execution_logs`.

To add a new node type, update all of the following: the `TaskType` enum, the `switch` in `DagPipelineOrchestrator.executeStep`, a custom node in `frontend/src/features/pipelines/components/nodes/` (use the shared `NodeHeaderActions` for its edit and delete buttons), and in `PipelineCanvas.tsx` its `nodeTypes` entry, its `labelMap` and `defaultDataMap` entries, and a palette button. Its form goes in `NodeConfigModal.tsx`. Add the value to the `TaskType` union in `frontend/src/types/pipeline.ts` as well.

**Other modules:**
- `execution/`: `ProcessExecutorService` wraps `ProcessBuilder` and takes an argument list, with a timeout watchdog and `RingBuffer` output capture. `ProcessSanitizer` validates executables, identifiers, hostnames and paths. Every external CLI call (`pg_dump`, `mysqldump`, `tar`…) must go through these two classes.
- `backup/`: `DatabaseBackupEngine` is implemented by `PostgreSqlBackupEngine` and `MySqlBackupEngine`. Dump stdout is streamed straight into a compressor and never buffered in memory. `FileSystemBackupEngine` handles tar.gz/zst/zip with exclusions, and `RetentionCleanupService` prunes old backups.
- `transfer/`: `ChunkSplitterEngine` and `ChecksumService` handle splitting and SHA-256. `RemoteTransferClient` is implemented by `FtpTransferClient` (commons-net) and `SftpTransferClient` (JSch). `ResilientTransferService` uploads chunk by chunk and retries only the chunk that failed. `StoragePrecheckService` checks free space before a transfer.
- `vault/`: `VaultService` does AES-256-GCM encryption for stored credentials. Pipeline steps reference credentials by `credentialId`.
- `drive/`: Google Drive upload plus a Firestore audit trail (`DriveUploadFacadeService`, `GoogleDriveService`, `FirestoreAuditService`). `GoogleCloudConfig` returns **null** `Drive`/`Firestore` beans when `custos.gcp.enabled=false` or the service-account JSON is missing, so consumers must null-check. The service-account file is gitignored (`credentials/`, `*service-account*.json`).
- `externalnotify/`: outbound HTTP to the CCenterV2 External Notify API (`ExternalNotifyClient` on `java.net.http.HttpClient`, `OutboundUrlValidator` for SSRF checks). The Domain is the credential's `host` and the `EXTERNAL_NOTIFY_AUTH_TOKEN` is its encrypted secret. Upstream 401/403 is mapped to 400 so the frontend's 401 refresh interceptor does not loop. Endpoints are `GET /api/v1/external-notify/tasks` (Connect) and `POST /api/v1/external-notify/send` (Call).
- `auth/`: stateless JWT auth (JJWT, 15-minute access tokens and 7-day refresh tokens stored in the DB), 5-strike / 15-minute lockout, and audit logs. RBAC is enforced by URL pattern in `config/SecurityConfig`. When you add a new `/api/v1/<module>/**` route, add a matcher there.

**Database:** Flyway migrations live in `resources/db/migration/V{n}__desc.sql`, and the main profile uses `ddl-auto: validate`. Any entity change therefore needs a new migration. Timestamps are UTC `Instant`. Tests use the `test` profile (`src/test/resources/application-test.yml`): H2 in PostgreSQL mode, Flyway **disabled**, `create-drop`, in-memory Quartz and GCP off. That means tests never exercise the migrations.

**Config/env:** `CUSTOS_JWT_SECRET`, `CUSTOS_VAULT_MASTER_KEY`, `CUSTOS_ADMIN_PASSWORD`, `SPRING_DATASOURCE_*`, `CUSTOS_GCP_ENABLED`, `GCP_SERVICE_ACCOUNT_KEY_PATH`, `GOOGLE_DRIVE_DEFAULT_FOLDER_ID`. The dev defaults are in `application.yml`, and the Docker image runs with `SPRING_PROFILES_ACTIVE=prod` (`application-prod.yml`).

## Frontend architecture

- Code is organized by feature in `src/features/<feature>/` (auth, users, vault, audit, tasks, pipelines, console, dashboard). Shared UI is in `src/components/`: `ui/` holds the shadcn-style primitives and `layout/` the app shell. The `@` alias maps to `src/`.
- API access goes through the axios instance in `services/api.ts` (`baseURL: /api/v1`). It attaches the bearer token from the Zustand `stores/authStore.ts` and transparently refreshes on 401 with a request queue. Per-module API files (`pipelineApi.ts`, `vaultApi.ts`…) sit next to it, with matching types in `src/types/`.
- TanStack Query holds server state and Zustand holds client-only state. Forms use React Hook Form with Zod. The pipeline builder uses `@xyflow/react`.
- Route guards in `App.tsx` are `ProtectedRoute` (any authenticated user) and `AdminRoute` (SUPER_ADMIN or ADMIN, used for users, vault and audit-logs).

## Rules from `rules.md` that apply to most changes

- Never build shell strings (`sh -c`, `cmd /c`, string concatenation). Pass a `List<String>` to `ProcessBuilder` and regex-validate DB, table and path inputs through `ProcessSanitizer`.
- Encrypt credentials with the vault before persisting them. Never log them or send them over STOMP.
- Every spawned process needs a timeout. On failure or cancel, call `destroyForcibly()` and delete temp files.
- Frontend: no `any` without a justifying comment. Every form needs a Zod schema. DAGs must pass cycle detection before save.
- Git: branch names `feat|fix|refactor/<module>-<desc>`, with Conventional Commit messages (`feat: ...`, `fix: ...`, `test: ...`).
