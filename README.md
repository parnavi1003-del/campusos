# CampusOS

Java Servlets + JDBC + MySQL backend, vanilla HTML/CSS/JS frontend.

## Run
1. MySQL: `mysql -u root -p < db/schema.sql`
2. Set DB login (defaults: root / root) before starting Tomcat:
   `CAMPUSOS_DB_USER`, `CAMPUSOS_DB_PASS`, optional `CAMPUSOS_DB_URL`
3. Build: `mvn clean package`
4. Copy `target/campusos.war` into Tomcat **10.1** `webapps/`
5. Open http://localhost:8080/campusos/

## Where the logic lives
| File | Role |
|---|---|
| `Engines.java` | Attendance, subject health, priority score, exam planner, lost & found matcher |
| `ApiServlet.java` | Auth + REST routes for every module + the Today engine |
| `Db.java` | JDBC helper |
| `webapp/app.js` | All screens and API calls |

## API (all JSON, under /api)
`auth/{register|login|logout|me}`, `today`, `subjects[/{id}/attend]`, `timetable`, `assignments[/{id}/done]`,
`exams`, `expenses`, `lostfound[/{id}/matches|resolve]`, `market[/{id}/close]`, `pulse[/{id}/answer|upvote]`

Passwords use SHA-256 (salted with the email) to keep the project simple; switch to BCrypt for real deployment.
