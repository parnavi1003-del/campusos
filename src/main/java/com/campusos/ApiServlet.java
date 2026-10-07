package com.campusos;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** One servlet, REST-style JSON API under /api/*. Routing: /api/{module}[/{id}[/{action}]] */
@WebServlet("/api/*")
public class ApiServlet extends HttpServlet {
    static final Gson G = new Gson();
    static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    static final double TARGET = 75;   // minimum attendance %

    static class HttpErr extends RuntimeException {
        final int code;
        HttpErr(int code, String msg) { super(msg); this.code = code; }
    }

    @Override
    protected void service(HttpServletRequest rq, HttpServletResponse rs) throws IOException {
        rs.setContentType("application/json");
        rs.setCharacterEncoding("UTF-8");
        try {
            rs.getWriter().write(G.toJson(route(rq)));
        } catch (HttpErr e) {
            rs.setStatus(e.code);
            rs.getWriter().write(G.toJson(Map.of("error", e.getMessage())));
        } catch (Exception e) {
            e.printStackTrace();
            rs.setStatus(500);
            rs.getWriter().write(G.toJson(Map.of("error", "Server error. Check the Tomcat log.")));
        }
    }

    // ================= routing =================
    Object route(HttpServletRequest rq) throws Exception {
        String path = rq.getPathInfo() == null ? "/" : rq.getPathInfo();
        String[] p = path.substring(1).split("/");
        String m = rq.getMethod();
        Map<String, Object> b = body(rq, m);
        if (p[0].equals("auth")) return auth(rq, p.length > 1 ? p[1] : "", b);

        HttpSession s = rq.getSession(false);
        if (s == null || s.getAttribute("uid") == null) throw new HttpErr(401, "Please log in");
        long uid = (Long) s.getAttribute("uid");
        long id = p.length > 1 && p[1].matches("\\d+") ? Long.parseLong(p[1]) : 0;
        String act = p.length > 2 ? p[2] : "";

        switch (p[0]) {
            case "today":       return today(uid);
            case "subjects":    return subjects(uid, m, id, act, b);
            case "timetable":   return timetable(uid, m, id, b);
            case "assignments": return assignments(uid, m, id, act, b);
            case "exams":       return exams(uid, m, id, b);
            case "expenses":    return expenses(uid, m, id, b);
            case "lostfound":   return lostFound(uid, m, id, act, b);
            case "market":      return market(uid, m, id, act, b);
            case "pulse":       return pulse(uid, m, id, act, b);
            default: throw new HttpErr(404, "Not found");
        }
    }

    // ================= helpers =================
    Map<String, Object> body(HttpServletRequest rq, String m) throws IOException {
        if (m.equals("GET") || m.equals("DELETE")) return new HashMap<>();
        Map<String, Object> b = G.fromJson(rq.getReader(), new TypeToken<Map<String, Object>>() {}.getType());
        return b == null ? new HashMap<>() : b;
    }
    static Map<String, Object> ok() { return Map.of("ok", true); }
    static double num(Object o) { return Engines.num(o); }
    static String S(Map<String, Object> b, String k) {
        Object o = b.get(k);
        if (o == null) return null;
        String s = o.toString().trim();
        return s.isEmpty() ? null : s;
    }
    static String req(Map<String, Object> b, String k) {
        String s = S(b, k);
        if (s == null) throw new HttpErr(400, "Missing field: " + k.replace('_', ' '));
        return s;
    }
    static double N(Map<String, Object> b, String k, double d) {
        String s = S(b, k);
        if (s == null) return d;
        try { return Double.parseDouble(s); } catch (Exception e) { return d; }
    }
    static Long L(Map<String, Object> b, String k) {
        String s = S(b, k);
        if (s == null) return null;
        try { return (long) Double.parseDouble(s); } catch (Exception e) { return null; }
    }
    static LocalDate D(Map<String, Object> b, String k) {
        try { return LocalDate.parse(req(b, k)); } catch (java.time.format.DateTimeParseException e) { throw new HttpErr(400, "Invalid date: " + k.replace('_', ' ')); }
    }
    static String cap(String s) { return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1); }
    /** subject id the user actually owns, else null */
    static Long ownedSubject(long uid, Long sid) throws Exception {
        return sid != null && Db.one("SELECT id FROM subjects WHERE id=? AND user_id=?", sid, uid) != null ? sid : null;
    }
    static String hash(String email, String pw) throws Exception {
        byte[] d = MessageDigest.getInstance("SHA-256").digest((email + ":" + pw).getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte x : d) sb.append(String.format("%02x", x));
        return sb.toString();
    }

    // ================= auth =================
    Object auth(HttpServletRequest rq, String act, Map<String, Object> b) throws Exception {
        switch (act) {
            case "register": {
                String name = req(b, "name"), email = req(b, "email").toLowerCase(), pw = req(b, "password");
                if (pw.length() < 6) throw new HttpErr(400, "Password must be at least 6 characters");
                if (Db.one("SELECT id FROM users WHERE email=?", email) != null) throw new HttpErr(409, "That email is already registered");
                long id = Db.insert("INSERT INTO users(name,email,password_hash) VALUES(?,?,?)", name, email, hash(email, pw));
                return login(rq, id, name);
            }
            case "login": {
                String email = req(b, "email").toLowerCase();
                Map<String, Object> u = Db.one("SELECT id,name FROM users WHERE email=? AND password_hash=?", email, hash(email, req(b, "password")));
                if (u == null) throw new HttpErr(401, "Wrong email or password");
                return login(rq, ((Number) u.get("id")).longValue(), (String) u.get("name"));
            }
            case "logout": {
                HttpSession s = rq.getSession(false);
                if (s != null) s.invalidate();
                return ok();
            }
            case "me": {
                HttpSession s = rq.getSession(false);
                if (s == null || s.getAttribute("uid") == null) throw new HttpErr(401, "Please log in");
                return Db.one("SELECT id,name,role FROM users WHERE id=?", s.getAttribute("uid"));
            }
            default: throw new HttpErr(404, "Not found");
        }
    }

    Object login(HttpServletRequest rq, long id, String name) {
        HttpSession old = rq.getSession(false);
        if (old != null) old.invalidate();
        rq.getSession(true).setAttribute("uid", id);
        return Map.of("id", id, "name", name);
    }

    // ================= subjects + attendance =================
    List<Map<String, Object>> subjectList(long uid) throws Exception {
        List<Map<String, Object>> rows = Db.rows("SELECT * FROM subjects WHERE user_id=? ORDER BY name", uid);
        for (Map<String, Object> r : rows) {
            Map<String, Object> att = Engines.attendance(num(r.get("present")), num(r.get("total")), TARGET);
            double h = Engines.health(num(att.get("pct")), num(r.get("marks")), num(r.get("assign_pct")), num(r.get("quiz_pct")));
            r.put("attendance", att);
            r.put("health", h);
            r.put("label", h >= 75 ? "Good" : h >= 60 ? "Needs attention" : "Critical");
        }
        return rows;
    }

    Object subjects(long uid, String m, long id, String act, Map<String, Object> b) throws Exception {
        if (m.equals("GET")) return subjectList(uid);
        if (m.equals("DELETE")) { Db.update("DELETE FROM subjects WHERE id=? AND user_id=?", id, uid); return ok(); }
        if (m.equals("POST") && act.equals("attend")) {
            boolean present = Boolean.parseBoolean(String.valueOf(b.get("present")));
            Db.update("UPDATE subjects SET total=total+1, present=present+? WHERE id=? AND user_id=?", present ? 1 : 0, id, uid);
            return ok();
        }
        if (m.equals("POST")) {
            String name = req(b, "name");
            int total = (int) N(b, "total", 0), present = Math.min((int) N(b, "present", 0), total);
            Db.insert("INSERT INTO subjects(user_id,name,present,total,marks,assign_pct,quiz_pct) VALUES(?,?,?,?,?,?,?)",
                uid, name, present, total, N(b, "marks", 0), N(b, "assign_pct", 0), N(b, "quiz_pct", 0));
            return ok();
        }
        throw new HttpErr(405, "Method not allowed");
    }

    Object timetable(long uid, String m, long id, Map<String, Object> b) throws Exception {
        if (m.equals("GET"))
            return Db.rows("SELECT t.id,t.day_of_week,t.start_time,t.title,s.name AS subject FROM timetable t " +
                "LEFT JOIN subjects s ON s.id=t.subject_id WHERE t.user_id=? ORDER BY t.day_of_week,t.start_time", uid);
        if (m.equals("DELETE")) { Db.update("DELETE FROM timetable WHERE id=? AND user_id=?", id, uid); return ok(); }
        if (m.equals("POST")) {
            Db.insert("INSERT INTO timetable(user_id,day_of_week,start_time,title,subject_id) VALUES(?,?,?,?,?)",
                uid, (int) N(b, "day_of_week", 1), req(b, "start_time"), req(b, "title"), ownedSubject(uid, L(b, "subject_id")));
            return ok();
        }
        throw new HttpErr(405, "Method not allowed");
    }

    // ================= assignments =================
    List<Map<String, Object>> assignmentList(long uid, LocalDate today) throws Exception {
        List<Map<String, Object>> rows = Db.rows(
            "SELECT a.*, s.name AS subject, s.present, s.total, s.marks, s.assign_pct, s.quiz_pct FROM assignments a " +
            "LEFT JOIN subjects s ON s.id=a.subject_id WHERE a.user_id=?", uid);
        for (Map<String, Object> r : rows) {
            double h = r.get("subject") == null ? 70 : Engines.health(Engines.pct(num(r.get("present")), num(r.get("total"))),
                num(r.get("marks")), num(r.get("assign_pct")), num(r.get("quiz_pct")));
            long days = ChronoUnit.DAYS.between(today, LocalDate.parse((String) r.get("deadline")));
            r.put("days_left", days);
            r.put("priority", Engines.priority(days, num(r.get("difficulty")), num(r.get("weightage")), h));
            for (String k : new String[]{"present", "total", "marks", "assign_pct", "quiz_pct"}) r.remove(k);
        }
        rows.sort(Comparator.comparing((Map<String, Object> r) -> Boolean.TRUE.equals(r.get("done")))
            .thenComparingDouble(r -> -num(r.get("priority"))));
        return rows;
    }

    Object assignments(long uid, String m, long id, String act, Map<String, Object> b) throws Exception {
        if (m.equals("GET")) return assignmentList(uid, LocalDate.now(IST));
        if (m.equals("DELETE")) { Db.update("DELETE FROM assignments WHERE id=? AND user_id=?", id, uid); return ok(); }
        if (m.equals("POST") && act.equals("done")) {
            Db.update("UPDATE assignments SET done=NOT done WHERE id=? AND user_id=?", id, uid);
            return ok();
        }
        if (m.equals("POST")) {
            int diff = (int) Math.max(1, Math.min(5, N(b, "difficulty", 3)));
            Db.insert("INSERT INTO assignments(user_id,subject_id,title,deadline,difficulty,weightage) VALUES(?,?,?,?,?,?)",
                uid, ownedSubject(uid, L(b, "subject_id")), req(b, "title"), D(b, "deadline"), diff, N(b, "weightage", 10));
            return ok();
        }
        throw new HttpErr(405, "Method not allowed");
    }

    // ================= exams =================
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> examList(long uid, LocalDate today) throws Exception {
        List<Map<String, Object>> exams = Db.rows("SELECT * FROM exams WHERE user_id=? ORDER BY exam_date", uid);
        List<Map<String, Object>> topics = Db.rows(
            "SELECT t.* FROM exam_topics t JOIN exams e ON e.id=t.exam_id WHERE e.user_id=? ORDER BY t.id", uid);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> e : exams) {
            LocalDate ed = LocalDate.parse((String) e.get("exam_date"));
            long days = ChronoUnit.DAYS.between(today, ed);
            if (days < 0) continue;
            List<Map<String, Object>> ts = new ArrayList<>();
            for (Map<String, Object> t : topics) if (num(t.get("exam_id")) == num(e.get("id"))) ts.add(t);
            e.put("days_left", days);
            e.put("topics", ts);
            e.put("plan", Engines.plan(today, ed, ts));
            out.add(e);
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    Object exams(long uid, String m, long id, Map<String, Object> b) throws Exception {
        if (m.equals("GET")) return examList(uid, LocalDate.now(IST));
        if (m.equals("DELETE")) { Db.update("DELETE FROM exams WHERE id=? AND user_id=?", id, uid); return ok(); }
        if (m.equals("POST")) {
            long eid = Db.insert("INSERT INTO exams(user_id,subject,exam_date) VALUES(?,?,?)", uid, req(b, "subject"), D(b, "exam_date"));
            Object ts = b.get("topics");
            if (ts instanceof List)
                for (Object o : (List<Object>) ts) {
                    Map<String, Object> t = (Map<String, Object>) o;
                    if (S(t, "name") != null)
                        Db.insert("INSERT INTO exam_topics(exam_id,name,weak) VALUES(?,?,?)", eid, S(t, "name"), Boolean.parseBoolean(String.valueOf(t.get("weak"))));
                }
            return ok();
        }
        throw new HttpErr(405, "Method not allowed");
    }

    // ================= expenses =================
    Map<String, Double> sums(long uid, LocalDate from, LocalDate to) throws Exception {
        Map<String, Double> r = new LinkedHashMap<>();
        for (Map<String, Object> x : Db.rows("SELECT category, SUM(amount) AS total FROM expenses " +
                "WHERE user_id=? AND spent_on>=? AND spent_on<? GROUP BY category ORDER BY total DESC", uid, from, to))
            r.put((String) x.get("category"), num(x.get("total")));
        return r;
    }

    Object expenses(long uid, String m, long id, Map<String, Object> b) throws Exception {
        if (m.equals("DELETE")) { Db.update("DELETE FROM expenses WHERE id=? AND user_id=?", id, uid); return ok(); }
        if (m.equals("POST")) {
            double amt = N(b, "amount", 0);
            if (amt <= 0) throw new HttpErr(400, "Enter an amount above 0");
            LocalDate on = S(b, "spent_on") == null ? LocalDate.now(IST) : D(b, "spent_on");
            Db.insert("INSERT INTO expenses(user_id,category,amount,note,spent_on) VALUES(?,?,?,?,?)",
                uid, req(b, "category").toLowerCase(), amt, S(b, "note"), on);
            return ok();
        }
        LocalDate today = LocalDate.now(IST), first = today.withDayOfMonth(1), prevFirst = first.minusMonths(1);
        LocalDate prevTo = prevFirst.plusDays(today.getDayOfMonth()).isAfter(first) ? first : prevFirst.plusDays(today.getDayOfMonth());
        Map<String, Double> cur = sums(uid, first, first.plusMonths(1)), prev = sums(uid, prevFirst, prevTo);
        double ct = cur.values().stream().mapToDouble(Double::doubleValue).sum();
        double pt = prev.values().stream().mapToDouble(Double::doubleValue).sum();

        List<String> insights = new ArrayList<>();
        for (Map.Entry<String, Double> e : cur.entrySet()) {
            double p = prev.getOrDefault(e.getKey(), 0.0);
            if (p > 0) {
                long ch = Math.round((e.getValue() - p) * 100 / p);
                if (Math.abs(ch) >= 10)
                    insights.add(cap(e.getKey()) + " expenses are " + Math.abs(ch) + "% " + (ch > 0 ? "higher" : "lower") + " than at this point last month.");
            }
        }
        if (pt > 0) {
            long ch = Math.round((ct - pt) * 100 / pt);
            insights.add(0, "Total spending is " + Math.abs(ch) + "% " + (ch >= 0 ? "higher" : "lower") + " than at this point last month.");
        }
        if (insights.isEmpty()) insights.add("Add a few more expenses and CampusOS will start comparing months.");

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("this_month", cur);
        out.put("last_month", prev);
        out.put("total", ct);
        out.put("last_total", pt);
        out.put("insights", insights);
        out.put("recent", Db.rows("SELECT id,category,amount,note,spent_on FROM expenses WHERE user_id=? ORDER BY spent_on DESC,id DESC LIMIT 15", uid));
        return out;
    }

    // ================= lost & found =================
    Object lostFound(long uid, String m, long id, String act, Map<String, Object> b) throws Exception {
        if (m.equals("GET") && act.equals("matches")) {
            Map<String, Object> me = Db.one("SELECT * FROM lost_found WHERE id=? AND user_id=?", id, uid);
            if (me == null) throw new HttpErr(404, "Item not found");
            String other = me.get("type").equals("lost") ? "found" : "lost";
            List<Map<String, Object>> out = new ArrayList<>();
            for (Map<String, Object> c : Db.rows("SELECT l.*, u.name AS poster FROM lost_found l JOIN users u ON u.id=l.user_id " +
                    "WHERE l.type=? AND l.status='open' AND l.user_id<>?", other, uid)) {
                int score = Engines.match(me, c);
                if (score >= 40) { c.put("score", score); out.add(c); }
            }
            out.sort(Comparator.comparingInt((Map<String, Object> c) -> (Integer) c.get("score")).reversed());
            return out;
        }
        if (m.equals("GET"))
            return Db.rows("SELECT l.*, u.name AS poster, (l.user_id=?) AS mine FROM lost_found l JOIN users u ON u.id=l.user_id " +
                "ORDER BY l.status, l.created_at DESC LIMIT 100", uid);
        if (m.equals("DELETE")) { Db.update("DELETE FROM lost_found WHERE id=? AND user_id=?", id, uid); return ok(); }
        if (m.equals("POST") && act.equals("resolve")) {
            Db.update("UPDATE lost_found SET status='resolved' WHERE id=? AND user_id=?", id, uid);
            return ok();
        }
        if (m.equals("POST")) {
            String type = req(b, "type");
            if (!type.equals("lost") && !type.equals("found")) throw new HttpErr(400, "Type must be lost or found");
            Db.insert("INSERT INTO lost_found(user_id,type,title,category,color,location,item_date,description) VALUES(?,?,?,?,?,?,?,?)",
                uid, type, req(b, "title"), S(b, "category"), S(b, "color"), S(b, "location"), D(b, "item_date"), S(b, "description"));
            return ok();
        }
        throw new HttpErr(405, "Method not allowed");
    }

    // ================= marketplace =================
    Object market(long uid, String m, long id, String act, Map<String, Object> b) throws Exception {
        if (m.equals("GET"))
            return Db.rows("SELECT k.*, u.name AS seller, (k.seller_id=?) AS mine FROM marketplace k JOIN users u ON u.id=k.seller_id " +
                "WHERE k.status='open' OR k.seller_id=? ORDER BY k.status, k.created_at DESC LIMIT 100", uid, uid);
        if (m.equals("DELETE")) { Db.update("DELETE FROM marketplace WHERE id=? AND seller_id=?", id, uid); return ok(); }
        if (m.equals("POST") && act.equals("close")) {
            Db.update("UPDATE marketplace SET status='closed' WHERE id=? AND seller_id=?", id, uid);
            return ok();
        }
        if (m.equals("POST")) {
            String type = S(b, "listing_type") == null ? "sell" : S(b, "listing_type");
            String cond = S(b, "item_condition") == null ? "good" : S(b, "item_condition");
            double price = type.equals("sell") ? Math.max(N(b, "price", 0), 0) : 0;
            Db.insert("INSERT INTO marketplace(seller_id,title,price,item_condition,category,listing_type) VALUES(?,?,?,?,?,?)",
                uid, req(b, "title"), price, cond, S(b, "category"), type);
            return ok();
        }
        throw new HttpErr(405, "Method not allowed");
    }

    // ================= campus pulse =================
    Object pulse(long uid, String m, long id, String act, Map<String, Object> b) throws Exception {
        if (m.equals("GET")) {
            List<Map<String, Object>> qs = Db.rows("SELECT q.*, u.name AS author FROM questions q JOIN users u ON u.id=q.user_id " +
                "ORDER BY q.upvotes DESC, q.created_at DESC LIMIT 50");
            Map<Long, List<Map<String, Object>>> byQ = new HashMap<>();
            for (Map<String, Object> a : Db.rows("SELECT a.*, u.name AS author FROM answers a JOIN users u ON u.id=a.user_id ORDER BY a.created_at"))
                byQ.computeIfAbsent(((Number) a.get("question_id")).longValue(), k -> new ArrayList<>()).add(a);
            for (Map<String, Object> q : qs) q.put("answers", byQ.getOrDefault(((Number) q.get("id")).longValue(), List.of()));
            return qs;
        }
        if (m.equals("POST") && act.equals("answer")) {
            Db.insert("INSERT INTO answers(question_id,user_id,body) VALUES(?,?,?)", id, uid, req(b, "body"));
            return ok();
        }
        if (m.equals("POST") && act.equals("upvote")) {
            if (Db.update("INSERT IGNORE INTO question_votes(question_id,user_id) VALUES(?,?)", id, uid) > 0)
                Db.update("UPDATE questions SET upvotes=upvotes+1 WHERE id=?", id);
            return ok();
        }
        if (m.equals("POST")) {
            Db.insert("INSERT INTO questions(user_id,title,body) VALUES(?,?,?)", uid, req(b, "title"), S(b, "body"));
            return ok();
        }
        if (m.equals("DELETE")) { Db.update("DELETE FROM questions WHERE id=? AND user_id=?", id, uid); return ok(); }
        throw new HttpErr(405, "Method not allowed");
    }

    // ================= the "Today" engine =================
    static void alert(List<Map<String, Object>> list, String level, String text) {
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("level", level);
        a.put("text", text);
        list.add(a);
    }

    Object today(long uid) throws Exception {
        LocalDate d = LocalDate.now(IST);
        String name = (String) Db.one("SELECT name FROM users WHERE id=?", uid).get("name");
        List<Map<String, Object>> subs = subjectList(uid);
        Map<Long, Map<String, Object>> byId = new HashMap<>();
        for (Map<String, Object> s : subs) byId.put(((Number) s.get("id")).longValue(), s);

        List<Map<String, Object>> alerts = new ArrayList<>();
        int dueToday = 0;

        // assignments
        for (Map<String, Object> a : assignmentList(uid, d)) {
            if (Boolean.TRUE.equals(a.get("done"))) continue;
            long days = (Long) a.get("days_left");
            String t = a.get("title") + (a.get("subject") != null ? " (" + a.get("subject") + ")" : "");
            if (days < 0) { alert(alerts, "warn", t + " is overdue"); dueToday++; }
            else if (days == 0) { alert(alerts, "warn", t + " is due today"); dueToday++; }
            else if (days <= 2) alert(alerts, "info", t + " is due in " + days + (days == 1 ? " day" : " days"));
        }
        // attendance
        Map<String, Object> worst = null;
        for (Map<String, Object> s : subs) {
            Map<String, Object> att = (Map<String, Object>) s.get("attendance");
            if (num(s.get("total")) == 0) continue;
            if (att.get("status").equals("low")) {
                alert(alerts, "warn", "Attendance warning: " + s.get("name") + " is at " + att.get("pct") + "%. Attend the next " + att.get("need") + " classes to reach 75%.");
                if (worst == null || num(att.get("pct")) < num(((Map<String, Object>) worst.get("attendance")).get("pct"))) worst = s;
            } else if (att.get("status").equals("close")) {
                alert(alerts, "info", s.get("name") + " attendance is " + att.get("pct") + "%, close to the 75% minimum.");
                if (worst == null) worst = s;
            }
        }
        // exams
        List<Map<String, Object>> exams = examList(uid, d);
        for (Map<String, Object> e : exams) {
            long days = (Long) e.get("days_left");
            if (days <= 7) alert(alerts, days <= 2 ? "warn" : "info", e.get("subject") + " exam " + (days == 0 ? "is today" : "in " + days + (days == 1 ? " day" : " days")));
        }

        // timetable for today
        List<Map<String, Object>> schedule = Db.rows("SELECT start_time, title, subject_id FROM timetable WHERE user_id=? AND day_of_week=? ORDER BY start_time",
            uid, d.getDayOfWeek().getValue());
        for (Map<String, Object> row : schedule) {
            Map<String, Object> s = row.get("subject_id") == null ? null : byId.get(((Number) row.get("subject_id")).longValue());
            if (s != null) {
                Map<String, Object> att = (Map<String, Object>) s.get("attendance");
                row.put("note", "Attendance " + att.get("pct") + "%" + (att.get("status").equals("low") ? ", below 75%" : ""));
            }
            row.remove("subject_id");
        }

        // recommended study: weak topic of nearest exam, else weakest subject
        String rec = null;
        for (Map<String, Object> e : exams) {
            for (Map<String, Object> t : (List<Map<String, Object>>) e.get("topics"))
                if (Boolean.TRUE.equals(t.get("weak"))) { rec = "Revise " + t.get("name") + " (" + e.get("subject") + ") for 45 minutes"; break; }
            if (rec != null) break;
        }
        if (rec == null && !subs.isEmpty()) {
            Map<String, Object> low = Collections.min(subs, Comparator.comparingDouble(s -> num(s.get("health"))));
            if (num(low.get("health")) < 75) rec = "Spend 45 minutes on " + low.get("name") + " (health " + (int) num(low.get("health")) + "/100)";
        }

        StringBuilder says = new StringBuilder();
        if (dueToday > 0) says.append("You have ").append(dueToday).append(dueToday == 1 ? " deadline" : " deadlines").append(" today");
        if (worst != null) says.append(says.length() > 0 ? " and your " : "Your ").append(worst.get("name")).append(" attendance needs attention");
        if (says.length() == 0) says.append("Nothing urgent. A good day to get ahead");
        says.append(".");

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("name", name);
        out.put("date", d.toString());
        out.put("count", alerts.size());
        out.put("alerts", alerts);
        out.put("schedule", schedule);
        out.put("recommendation", rec);
        out.put("says", says.toString());
        return out;
    }
}
