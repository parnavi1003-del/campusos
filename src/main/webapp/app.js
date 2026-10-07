const $ = (s, r = document) => r.querySelector(s);
const esc = s => String(s ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const DAYS = ['', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'];
const today = () => new Date(Date.now() - new Date().getTimezoneOffset() * 60000).toISOString().slice(0, 10);
const tone = h => h >= 75 ? 'good' : h >= 60 ? 'mid' : 'bad';
const empty = t => `<div class="empty">${t}</div>`;
const inr = n => '₹' + Number(n || 0).toLocaleString('en-IN', { maximumFractionDigits: 0 });
const opts = (arr, sel) => arr.map(([v, l]) => `<option value="${esc(v)}" ${v === sel ? 'selected' : ''}>${esc(l)}</option>`).join('');

let current = 'today', user = null;

async function api(path, method = 'GET', body) {
  const r = await fetch('api/' + path, {
    method, credentials: 'same-origin',
    headers: { 'Content-Type': 'application/json' },
    body: body ? JSON.stringify(body) : undefined
  });
  const j = await r.json().catch(() => ({}));
  if (!r.ok) {
    if (r.status === 401 && !path.startsWith('auth/')) showAuth();
    throw new Error(j.error || 'Something went wrong');
  }
  return j;
}

function toast(msg) {
  const t = $('#toast'); t.textContent = msg; t.classList.add('show');
  clearTimeout(toast.t); toast.t = setTimeout(() => t.classList.remove('show'), 2800);
}

/* ---------- views: each returns an HTML string ---------- */
const views = {};

views.today = async () => {
  const t = await api('today');
  const h = new Date().getHours();
  const hello = h < 12 ? 'Good morning' : h < 17 ? 'Good afternoon' : 'Good evening';
  const sched = t.schedule.map(s => `<div class="slot"><time>${esc(s.start_time.slice(0, 5))}</time>${esc(s.title)}${s.note ? ` <span class="muted">· ${esc(s.note)}</span>` : ''}</div>`).join('');
  const rec = t.recommendation ? `<div class="slot rec"><time>Suggested</time>${esc(t.recommendation)}</div>` : '';
  return `<h1 class="hello">${hello}, ${esc(t.name.split(' ')[0])}.</h1>
    <p class="muted">${t.count ? `You have ${t.count} ${t.count === 1 ? 'thing' : 'things'} to handle today.` : 'Nothing needs attention right now.'}</p>
    ${t.alerts.map(a => `<div class="alert ${a.level}">${esc(a.text)}</div>`).join('')}
    <h2>Your day</h2>
    ${sched || rec ? `<div class="timeline">${sched}${rec}</div>` : empty('No classes on your timetable today. Add them under Attendance.')}
    <div class="says"><b>CampusOS says:</b> ${esc(t.says)}</div>`;
};

views.subjects = async () => {
  const [subs, tt] = await Promise.all([api('subjects'), api('timetable')]);
  window._subs = subs;
  const cards = subs.map(s => {
    const a = s.attendance;
    const msg = a.need > 0
      ? `Attend the next <b>${a.need}</b> classes in a row to reach 75%.`
      : `You can miss <b>${a.can_miss}</b> more ${a.can_miss === 1 ? 'class' : 'classes'} and stay above 75%.`;
    return `<article class="card">
      <div class="row"><h3>${esc(s.name)}</h3><span class="chip ${tone(s.health)}">${esc(s.label)}</span></div>
      <div class="big">${a.pct}%</div><small>${s.present} of ${s.total} classes</small>
      <p>${msg}</p><p class="muted">If you miss the next class: ${a.if_miss_next}%</p>
      <div class="bar"><i class="${tone(s.health)}" style="width:${s.health}%"></i></div>
      <small>Subject health ${s.health}/100</small>
      <div class="row start" style="margin-top:.7rem">
        <button data-do="attend" data-id="${s.id}" data-present="true">Present</button>
        <button class="ghost" data-do="attend" data-id="${s.id}" data-present="false">Absent</button>
        <button class="danger" data-do="delSubject" data-id="${s.id}">Remove</button>
      </div></article>`;
  }).join('');
  const byDay = d => tt.filter(x => x.day_of_week === d).map(x =>
    `<div class="item"><span><b>${x.start_time.slice(0, 5)}</b> ${esc(x.title)}${x.subject ? ` <span class="muted">(${esc(x.subject)})</span>` : ''}</span>
     <button class="danger" data-do="delSlot" data-id="${x.id}">Remove</button></div>`).join('');
  const week = [1, 2, 3, 4, 5, 6, 7].filter(d => byDay(d)).map(d => `<h3 style="margin-top:1rem">${DAYS[d]}</h3>${byDay(d)}`).join('');
  return `<h1>Attendance</h1>
    <p class="muted">Health = 30% attendance + 30% marks + 20% assignments + 20% quizzes.</p>
    <div class="grid">${cards || ''}</div>${subs.length ? '' : empty('No subjects yet. Add your first one below.')}
    <details class="card"><summary>Add subject</summary>
      <form data-do="addSubject" class="form cols">
        <label>Subject<input name="name" required></label>
        <label>Classes attended<input name="present" type="number" min="0" value="0"></label>
        <label>Total classes<input name="total" type="number" min="0" value="0"></label>
        <label>Marks %<input name="marks" type="number" min="0" max="100" value="0"></label>
        <label>Assignments %<input name="assign_pct" type="number" min="0" max="100" value="0"></label>
        <label>Quiz %<input name="quiz_pct" type="number" min="0" max="100" value="0"></label>
        <button>Add subject</button></form></details>
    <h2>Timetable</h2>
    <details class="card"><summary>Add class slot</summary>
      <form data-do="addSlot" class="form cols">
        <label>Day<select name="day_of_week">${opts(DAYS.slice(1).map((d, i) => [String(i + 1), d]))}</select></label>
        <label>Time<input name="start_time" type="time" required></label>
        <label>Title<input name="title" placeholder="Java lecture" required></label>
        <label>Subject<select name="subject_id"><option value="">None</option>${opts(subs.map(s => [String(s.id), s.name]))}</select></label>
        <button>Add slot</button></form></details>
    ${week ? `<div class="card">${week}</div>` : empty('No classes on your timetable yet.')}`;
};

views.assignments = async () => {
  const [list, subs] = await Promise.all([api('assignments'), api('subjects')]);
  const first = list.find(a => !a.done);
  const row = a => `<div class="item ${a.done ? 'done' : ''}">
    <div><b>${esc(a.title)}</b> <span class="muted">${esc(a.subject || '')}</span><br>
      <small>${a.days_left < 0 ? 'Overdue' : a.days_left === 0 ? 'Due today' : 'Due in ' + a.days_left + ' days'} · difficulty ${a.difficulty}/5 · ${a.weightage} marks</small></div>
    <div class="row start"><span class="chip ${a.priority >= 70 ? 'bad' : a.priority >= 50 ? 'mid' : 'good'}">Priority ${a.priority}</span>
      <button class="ghost" data-do="toggleAssign" data-id="${a.id}">${a.done ? 'Undo' : 'Done'}</button>
      <button class="danger" data-do="delAssign" data-id="${a.id}">Delete</button></div></div>`;
  return `<h1>Assignments</h1>
    ${first ? `<div class="card first"><b>Do this first:</b> ${esc(first.title)}${first.subject ? ' (' + esc(first.subject) + ')' : ''}</div>` : ''}
    <details class="card"><summary>Add assignment</summary>
      <form data-do="addAssign" class="form cols">
        <label>Title<input name="title" required></label>
        <label>Subject<select name="subject_id"><option value="">None</option>${opts(subs.map(s => [String(s.id), s.name]))}</select></label>
        <label>Deadline<input name="deadline" type="date" min="${today()}" required></label>
        <label>Difficulty (1-5)<input name="difficulty" type="number" min="1" max="5" value="3"></label>
        <label>Marks weightage<input name="weightage" type="number" min="0" max="100" value="10"></label>
        <button>Add assignment</button></form></details>
    <div class="card">${list.map(row).join('') || 'No assignments yet.'}</div>`;
};

views.exams = async () => {
  const exams = await api('exams');
  const card = e => `<article class="card"><div class="row"><h3>${esc(e.subject)}</h3>
      <span class="chip ${e.days_left <= 3 ? 'bad' : 'mid'}">${e.days_left === 0 ? 'Today' : e.days_left + ' days left'}</span></div>
    <small>${esc(e.exam_date)} · weak: ${e.topics.filter(t => t.weak).map(t => esc(t.name)).join(', ') || 'none'}</small>
    ${e.plan.map(p => `<div class="item"><span><b>${esc(p.day)}</b> <span class="muted">${esc(p.date)}</span></span><span>${esc(p.task)}</span></div>`).join('') || '<p class="muted">Exam is today. Good luck!</p>'}
    <button class="danger" data-do="delExam" data-id="${e.id}" style="margin-top:.6rem">Delete exam</button></article>`;
  return `<h1>Exam planner</h1>
    <details class="card" open><summary>New exam</summary>
      <form data-do="addExam" class="form">
        <div class="form cols"><label>Subject<input name="subject" required></label>
          <label>Exam date<input name="exam_date" type="date" min="${today()}" required></label></div>
        <label>Strong topics (one per line)<textarea name="strong" rows="3"></textarea></label>
        <label>Weak topics (one per line)<textarea name="weak" rows="3"></textarea></label>
        <button>Create plan</button></form></details>
    ${exams.map(card).join('') || empty('No upcoming exams. Create one to get a day-by-day plan.')}`;
};

views.expenses = async () => {
  const x = await api('expenses');
  const cats = Object.entries(x.this_month);
  const max = Math.max(1, ...cats.map(c => c[1]));
  return `<h1>Expenses</h1>
    <div class="card"><div class="big">${inr(x.total)}</div><small>spent this month</small>
      ${x.insights.map(i => `<p>${esc(i)}</p>`).join('')}</div>
    <details class="card"><summary>Add expense</summary>
      <form data-do="addExpense" class="form cols">
        <label>Category<select name="category">${opts(['food', 'transport', 'education', 'entertainment', 'other'].map(c => [c, c[0].toUpperCase() + c.slice(1)]))}</select></label>
        <label>Amount (₹)<input name="amount" type="number" min="1" step="0.01" required></label>
        <label>Note<input name="note"></label>
        <label>Date<input name="spent_on" type="date" value="${today()}"></label>
        <button>Add expense</button></form></details>
    <div class="card"><h3>By category</h3>${cats.map(([c, v]) => `<div class="cat"><span>${esc(c)}</span>
      <div class="bar"><i style="width:${v / max * 100}%"></i></div><span>${inr(v)}</span></div>`).join('') || 'No expenses this month.'}</div>
    <div class="card"><h3>Recent</h3>${x.recent.map(e => `<div class="item"><span><b>${inr(e.amount)}</b> ${esc(e.category)} <span class="muted">${esc(e.note || '')}</span><br><small>${esc(e.spent_on)}</small></span>
      <button class="danger" data-do="delExpense" data-id="${e.id}">Delete</button></div>`).join('') || 'Nothing yet.'}</div>`;
};

views.lostfound = async () => {
  const list = await api('lostfound');
  const card = l => `<article class="card"><div class="row"><h3>${esc(l.title)}</h3>
      <span class="chip ${l.type}">${l.type === 'lost' ? 'Lost' : 'Found'}${l.status === 'resolved' ? ' · resolved' : ''}</span></div>
    <p class="muted">${[l.category, l.color, l.location].filter(Boolean).map(esc).join(' · ')}</p>
    <small>${esc(l.item_date)} · posted by ${esc(l.poster)}</small><p>${esc(l.description || '')}</p>
    ${l.mine && l.status === 'open' ? `<div class="row start">
      <button data-do="matches" data-id="${l.id}">Find matches</button>
      <button class="ghost" data-do="resolveLF" data-id="${l.id}">Mark resolved</button>
      <button class="danger" data-do="delLF" data-id="${l.id}">Delete</button></div><div id="m-${l.id}"></div>` : ''}</article>`;
  return `<h1>Lost &amp; found</h1>
    <details class="card"><summary>Post an item</summary>
      <form data-do="addLF" class="form cols">
        <label>I<select name="type"><option value="lost">lost something</option><option value="found">found something</option></select></label>
        <label>Item<input name="title" placeholder="Black water bottle" required></label>
        <label>Category<input name="category" placeholder="Bottle, ID card, calculator"></label>
        <label>Colour<input name="color"></label>
        <label>Location<input name="location" placeholder="Library, Block B"></label>
        <label>Date<input name="item_date" type="date" value="${today()}" required></label>
        <label>Description<input name="description"></label>
        <button>Post</button></form></details>
    <div class="grid">${list.map(card).join('')}</div>${list.length ? '' : empty('Nothing posted yet.')}`;
};

views.market = async () => {
  const list = await api('market');
  const card = k => `<article class="card"><div class="row"><h3>${esc(k.title)}</h3>
      <span class="chip ${k.status === 'open' ? 'good' : ''}">${k.listing_type === 'sell' ? inr(k.price) : k.listing_type === 'free' ? 'Free' : 'Exchange'}</span></div>
    <p class="muted">${[k.category, k.item_condition].filter(Boolean).map(esc).join(' · ')}</p>
    <small>Seller: ${esc(k.seller)}${k.status === 'closed' ? ' · closed' : ''}</small>
    ${k.mine ? `<div class="row start" style="margin-top:.6rem">${k.status === 'open' ? `<button class="ghost" data-do="closeListing" data-id="${k.id}">Mark closed</button>` : ''}
      <button class="danger" data-do="delListing" data-id="${k.id}">Delete</button></div>` : ''}</article>`;
  return `<h1>Marketplace</h1>
    <details class="card"><summary>New listing</summary>
      <form data-do="addListing" class="form cols">
        <label>Item<input name="title" required></label>
        <label>Type<select name="listing_type">${opts([['sell', 'Sell'], ['exchange', 'Exchange'], ['free', 'Give away']])}</select></label>
        <label>Price (₹)<input name="price" type="number" min="0"></label>
        <label>Condition<select name="item_condition">${opts([['new', 'New'], ['good', 'Good'], ['fair', 'Fair']], 'good')}</select></label>
        <label>Category<input name="category" placeholder="Books, calculator"></label>
        <button>Post listing</button></form></details>
    <div class="grid">${list.map(card).join('')}</div>${list.length ? '' : empty('No listings yet.')}`;
};

views.pulse = async () => {
  const qs = await api('pulse');
  const q = x => `<div class="q"><div class="row"><b>${esc(x.title)}</b>
      <button class="ghost" data-do="upvote" data-id="${x.id}">▲ ${x.upvotes}</button></div>
    <small>${esc(x.author)}</small><p>${esc(x.body || '')}</p>
    ${x.answers.map(a => `<div class="ans">${esc(a.body)} <small>${esc(a.author)}</small></div>`).join('')}
    <form data-do="answer" data-id="${x.id}" class="row" style="margin-top:.5rem;flex-wrap:nowrap">
      <input name="body" placeholder="Write an answer" required><button>Answer</button></form></div>`;
  return `<h1>Campus pulse</h1>
    <details class="card"><summary>Ask the campus</summary>
      <form data-do="ask" class="form"><input name="title" placeholder="Question or announcement" required>
        <textarea name="body" rows="2" placeholder="Details (optional)"></textarea><button>Post</button></form></details>
    <div class="card">${qs.map(q).join('') || 'No questions yet. Start the first discussion.'}</div>`;
};

/* ---------- actions ---------- */
const lines = s => (s || '').split('\n').map(x => x.trim()).filter(Boolean);
const actions = {
  login: async d => { user = await api('auth/login', 'POST', d); enter(); },
  register: async d => { user = await api('auth/register', 'POST', d); enter(); },
  showRegister: () => { $('#f-login').hidden = true; $('#f-register').hidden = false; },
  showLogin: () => { $('#f-login').hidden = false; $('#f-register').hidden = true; },
  logout: async () => { await api('auth/logout', 'POST'); showAuth(); },

  addSubject: async d => { await api('subjects', 'POST', d); go(); },
  delSubject: async d => { if (confirm('Remove this subject?')) { await api('subjects/' + d.id, 'DELETE'); go(); } },
  attend: async d => { await api(`subjects/${d.id}/attend`, 'POST', { present: d.present === 'true' }); go(); },
  addSlot: async d => { await api('timetable', 'POST', d); go(); },
  delSlot: async d => { await api('timetable/' + d.id, 'DELETE'); go(); },

  addAssign: async d => { await api('assignments', 'POST', d); go(); },
  toggleAssign: async d => { await api(`assignments/${d.id}/done`, 'POST', {}); go(); },
  delAssign: async d => { await api('assignments/' + d.id, 'DELETE'); go(); },

  addExam: async d => {
    const topics = [...lines(d.strong).map(name => ({ name, weak: false })), ...lines(d.weak).map(name => ({ name, weak: true }))];
    await api('exams', 'POST', { subject: d.subject, exam_date: d.exam_date, topics }); go();
  },
  delExam: async d => { await api('exams/' + d.id, 'DELETE'); go(); },

  addExpense: async d => { await api('expenses', 'POST', d); go(); },
  delExpense: async d => { await api('expenses/' + d.id, 'DELETE'); go(); },

  addLF: async d => { await api('lostfound', 'POST', d); go(); },
  resolveLF: async d => { await api(`lostfound/${d.id}/resolve`, 'POST', {}); go(); },
  delLF: async d => { await api('lostfound/' + d.id, 'DELETE'); go(); },
  matches: async d => {
    const m = await api(`lostfound/${d.id}/matches`);
    $('#m-' + d.id).innerHTML = m.length
      ? m.map(x => `<div class="alert"><b>Possible match: ${x.score}%</b> · ${esc(x.title)} (${esc(x.location || 'no location')}), posted by ${esc(x.poster)}</div>`).join('')
      : '<p class="muted">No likely matches yet. We will show them as soon as someone posts a similar item.</p>';
  },

  addListing: async d => { await api('market', 'POST', d); go(); },
  closeListing: async d => { await api(`market/${d.id}/close`, 'POST', {}); go(); },
  delListing: async d => { await api('market/' + d.id, 'DELETE'); go(); },

  ask: async d => { await api('pulse', 'POST', d); go(); },
  answer: async (d, f) => { await api(`pulse/${f.dataset.id}/answer`, 'POST', d); go(); },
  upvote: async d => { await api(`pulse/${d.id}/upvote`, 'POST', {}); go(); },
};

document.addEventListener('submit', async e => {
  const f = e.target.closest('form[data-do]');
  if (!f) return;
  e.preventDefault();
  try { await actions[f.dataset.do](Object.fromEntries(new FormData(f)), f); } catch (err) { toast(err.message); }
});
document.addEventListener('click', async e => {
  const b = e.target.closest('[data-do]:not(form)');
  if (b) {
    e.preventDefault();
    try { await actions[b.dataset.do](b.dataset); } catch (err) { toast(err.message); }
    return;
  }
  const v = e.target.closest('[data-view]');
  if (v) go(v.dataset.view);
});

/* ---------- navigation ---------- */
async function go(view) {
  if (view) current = view;
  document.querySelectorAll('#rail [data-view]').forEach(b => b.classList.toggle('on', b.dataset.view === current));
  try { $('#view').innerHTML = await views[current](); }
  catch (err) { if (err.message !== 'Please log in') $('#view').innerHTML = empty(esc(err.message)); }
}
function enter() { $('#auth').hidden = true; $('#app').hidden = false; go('today'); }
function showAuth() { $('#app').hidden = true; $('#auth').hidden = false; }

(async () => {
  try { user = await api('auth/me'); enter(); } catch { showAuth(); }
})();
