# To-Do Task Manager

Full-stack task manager: **frontend** (HTML/CSS/JS) + **backend** (Java Spring Boot REST API) + **PostgreSQL** database (Supabase).

```
todo-manager/
├── backend/     Spring Boot API (Maven)  -> http://localhost:8080
├── frontend/    index.html, style.css, app.js, config.js
└── api-tests/   Postman collection + curl smoke-test script
```

## Features
Add / view / edit / delete tasks, Pending/Completed status, filter (All / Pending / Completed / Overdue), title validation with error messages, data stored in the database.
Bonus: search, priority (Low/Medium/High), overdue highlighting.

## Run locally

### 1. Backend (needs Java 17+ and Maven)
**Option A – Supabase/PostgreSQL (default):**
```bash
cd backend
export DB_PASSWORD='your-supabase-db-password'     # Windows: set DB_PASSWORD=...
mvn spring-boot:run
```
Optional overrides: `DB_URL`, `DB_USERNAME`. The `tasks` table is created automatically
(or run `backend/db/schema.sql` in the Supabase SQL editor).

**Option B – no setup, local file database (H2):**
```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=local
```
Data is saved in `backend/data/` and survives restarts.

### 2. Frontend
Serve the `frontend` folder with any static server, then open it in the browser:
```bash
cd frontend
python3 -m http.server 5500        # open http://localhost:5500
```
(or use the VS Code "Live Server" extension). The API address is set in `frontend/config.js`.

## REST API
| Method | URL | Purpose |
|---|---|---|
| GET | `/api/tasks?status=Pending\|Completed\|All&search=text&overdue=true` | list / filter / search |
| GET | `/api/tasks/{id}` | one task |
| POST | `/api/tasks` | create `{"title","description","deadline":"yyyy-MM-dd","priority"}` |
| PUT | `/api/tasks/{id}` | edit title/description/deadline/priority |
| PATCH | `/api/tasks/{id}/status` | `{"status":"Pending"\|"Completed"}` |
| DELETE | `/api/tasks/{id}` | delete (204) |

Errors are returned as `{"error":"message"}` (e.g. empty title → `400 "Title must not be empty"`).

## Testing
- **Automated (JUnit + MockMvc, in-memory H2):** `cd backend && mvn test`
- **Live smoke test:** `./api-tests/api-tests.sh http://localhost:8080`
- **Postman:** import `api-tests/todo-manager.postman_collection.json` (variable `baseUrl`).

## Deploy online

### 1. Database (Supabase / Render PostgreSQL)
- **Supabase**: Create a free project at [supabase.com](https://supabase.com). Copy the JDBC connection string from Project Settings > Database, or run `backend/db/schema.sql` in the SQL Editor.
- **Render PostgreSQL**: Create a PostgreSQL instance on Render and copy either the `Internal Database URL` or set `DATABASE_URL`.

### 2. Backend (Render Web Service)
1. In [Render Dashboard](https://dashboard.render.com), click **New + > Web Service**.
2. Connect your repository.
3. Configure settings:
   - **Root Directory**: `backend` (or leave empty if using `render.yaml`)
   - **Runtime**: `Docker` (Dockerfile path: `Dockerfile`)
   - **Health Check Path**: `/api/tasks/health`
4. Set Environment Variables:
   - `DB_URL`: your database connection string (e.g. `jdbc:postgresql://db.xxxx.supabase.co:5432/postgres` or `postgres://...`)
   - `DB_USERNAME`: database username (e.g. `postgres`)
   - `DB_PASSWORD`: database password
   - `CORS_ALLOWED_ORIGINS`: your deployed frontend URL (e.g. `https://my-todo.vercel.app` or `*`)

*(Tip: You can also use the included `render.yaml` Blueprint to automatically configure both services).*

### 3. Frontend (Vercel / Netlify / Render Static Site)
1. Open `frontend/config.js` and set `API_BASE_URL` to your live backend URL:
   ```javascript
   window.APP_CONFIG = {
     API_BASE_URL: 'https://your-backend-app.onrender.com'
   };
   ```
2. Deploy the `frontend/` folder:
   - **Vercel**: Import project, set root directory to `frontend`.
   - **Netlify**: Drag and drop the `frontend` folder or connect git with publish directory `frontend`.
   - **Render Static Site**: Create a Static Site with publish directory `frontend`.

> Security note: never commit your database password to git; supply it via environment variables in your deployment dashboard.

