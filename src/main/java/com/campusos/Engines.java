package com.campusos;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** The "smart" part: attendance, subject health, priority, exam planner, lost & found matcher. */
public class Engines {
    static double num(Object o) { return o instanceof Number ? ((Number) o).doubleValue() : 0; }
    static double round1(double v) { return Math.round(v * 10) / 10.0; }

    // ---------- Attendance Intelligence ----------
    public static double pct(double present, double total) { return total <= 0 ? 100 : present * 100.0 / total; }

    public static Map<String, Object> attendance(double p, double t, double target) {
        Map<String, Object> m = new LinkedHashMap<>();
        double cur = pct(p, t);
        m.put("pct", round1(cur));
        if (cur >= target) {                       // how many can I still miss?
            m.put("need", 0);
            m.put("can_miss", Math.max((int) Math.floor(p * 100.0 / target - t + 1e-9), 0));
        } else {                                   // how many in a row must I attend?
            m.put("need", (int) Math.ceil((target * t - 100 * p) / (100 - target) - 1e-9));
            m.put("can_miss", 0);
        }
        m.put("if_miss_next", round1(pct(p, t + 1)));
        m.put("status", cur >= target + 5 ? "safe" : cur >= target ? "close" : "low");
        return m;
    }

    // ---------- Subject Health = 30% attendance + 30% marks + 20% assignments + 20% quiz ----------
    public static double health(double att, double marks, double assign, double quiz) {
        return Math.round(0.3 * att + 0.3 * marks + 0.2 * assign + 0.2 * quiz);
    }

    // ---------- Priority = deadline urgency + difficulty + marks weightage + subject weakness (max 100) ----------
    public static double priority(long daysLeft, double difficulty, double weightage, double health) {
        double urgency = daysLeft <= 0 ? 40 : daysLeft == 1 ? 35 : daysLeft == 2 ? 30 : daysLeft <= 4 ? 20 : daysLeft <= 7 ? 10 : 5;
        return Math.round(urgency + difficulty * 5 + Math.min(weightage, 20) * 1.25 + (100 - health) * 0.1);
    }

    // ---------- Exam planner (rule based) ----------
    // weak topics get two slots (study + practice) and come first; last days are revision and a mock test.
    public static List<Map<String, Object>> plan(LocalDate today, LocalDate exam, List<Map<String, Object>> topics) {
        List<Map<String, Object>> out = new ArrayList<>();
        long n = ChronoUnit.DAYS.between(today, exam);
        if (n <= 0) return out;
        List<String> slots = new ArrayList<>();
        for (Map<String, Object> t : topics) if (Boolean.TRUE.equals(t.get("weak"))) { slots.add("" + t.get("name")); slots.add(t.get("name") + " practice"); }
        for (Map<String, Object> t : topics) if (!Boolean.TRUE.equals(t.get("weak"))) slots.add("" + t.get("name"));
        int reserve = n >= 5 ? 2 : n >= 3 ? 1 : 0;
        int study = (int) n - reserve, L = slots.size();
        for (int i = 0; i < study; i++) {
            String task;
            if (L == 0) task = "Free study";
            else {
                int from = Math.min(i * L / study, L - 1);
                int to = Math.min(Math.max((i + 1) * L / study, from + 1), L);
                task = String.join(" + ", slots.subList(from, to));
            }
            out.add(day(i, today, task));
        }
        int k = study;
        if (reserve == 2) out.add(day(k++, today, "Revision + weak topics"));
        if (reserve >= 1) out.add(day(k, today, "Mock test"));
        return out;
    }

    static Map<String, Object> day(int i, LocalDate today, String task) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("day", "Day " + (i + 1));
        m.put("date", today.plusDays(i).toString());
        m.put("task", task);
        return m;
    }

    // ---------- Lost & Found matcher (0-100) ----------
    // category 30 + colour 20 + location 20 + date closeness 15 + title/description word overlap 15
    static String s(Object o) { return o == null ? "" : o.toString().trim().toLowerCase(); }

    static Set<String> tokens(Map<String, Object> m) {
        Set<String> t = new HashSet<>();
        for (String w : (s(m.get("title")) + " " + s(m.get("description"))).split("[^a-z0-9]+")) if (w.length() > 2) t.add(w);
        return t;
    }

    public static int match(Map<String, Object> a, Map<String, Object> b) {
        double sc = 0;
        if (!s(a.get("category")).isEmpty() && s(a.get("category")).equals(s(b.get("category")))) sc += 30;
        if (!s(a.get("color")).isEmpty() && s(a.get("color")).equals(s(b.get("color")))) sc += 20;
        String la = s(a.get("location")), lb = s(b.get("location"));
        if (!la.isEmpty() && !lb.isEmpty()) sc += la.equals(lb) ? 20 : (la.contains(lb) || lb.contains(la)) ? 10 : 0;
        try {
            long d = Math.abs(ChronoUnit.DAYS.between(LocalDate.parse(s(a.get("item_date"))), LocalDate.parse(s(b.get("item_date")))));
            sc += d <= 1 ? 15 : d <= 3 ? 10 : d <= 7 ? 5 : 0;
        } catch (Exception ignored) { }
        Set<String> ta = tokens(a), tb = tokens(b);
        if (!ta.isEmpty() && !tb.isEmpty()) {
            Set<String> inter = new HashSet<>(ta); inter.retainAll(tb);
            Set<String> union = new HashSet<>(ta); union.addAll(tb);
            sc += 15.0 * inter.size() / union.size();
        }
        return (int) Math.round(sc);
    }
}
