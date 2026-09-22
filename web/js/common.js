// ==========================================
// 대타구함 - 공통 스크립트
// ==========================================
(function () {
  // ---------- 하단 탭 메뉴 ----------
  // <nav class="tabbar" data-role="worker" data-active="home"></nav> 처럼 두면 자동으로 그려요.
  const ICON = {
    calendar: '<rect x="3" y="4" width="18" height="18" rx="2"/><path d="M16 2v4M8 2v4M3 10h18"/>',
    swap: '<path d="M17 1l4 4-4 4"/><path d="M3 11V9a4 4 0 0 1 4-4h14"/><path d="M7 23l-4-4 4-4"/><path d="M21 13v2a4 4 0 0 1-4 4H3"/>',
    bell: '<path d="M6 8a6 6 0 1 1 12 0c0 7 3 9 3 9H3s3-2 3-9"/><path d="M10.3 21a1.94 1.94 0 0 0 3.4 0"/>',
    user: '<circle cx="12" cy="8" r="4"/><path d="M4 21a8 8 0 0 1 16 0"/>',
    home: '<path d="M3 10l9-7 9 7v10a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/>',
    check: '<path d="M20 6L9 17l-5-5"/>',
    users: '<circle cx="9" cy="8" r="4"/><path d="M1 21a8 8 0 0 1 16 0"/><path d="M17 4a4 4 0 0 1 0 8M23 21a8 8 0 0 0-5-7.4"/>',
    store: '<path d="M3 9l1-5h16l1 5"/><path d="M4 9v11h16V9"/><path d="M9 20v-6h6v6"/>',
    plus: '<path d="M12 5v14M5 12h14"/>',
    gear: '<circle cx="12" cy="12" r="3"/><path d="M12 2v3M12 19v3M4.2 4.2l2.1 2.1M17.7 17.7l2.1 2.1M2 12h3M19 12h3M4.2 19.8l2.1-2.1M17.7 6.3l2.1-2.1"/>',
    shield: '<path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/>',
  };

  // 경로는 역할 폴더 기준 상대 경로 (../역할/파일)
  const TABS = {
    worker: [
      ["home", "내 근무", "calendar", "../worker/home.html"],
      ["board", "요청", "swap", "../worker/board.html"],
      ["noti", "알림", "bell", "../worker/notifications.html"],
      ["me", "내 정보", "user", "../worker/me.html"],
    ],
    manager: [
      ["today", "TODAY", "home", "../manager/today.html"],
      ["schedule", "근무표", "calendar", "../manager/schedule-bulk.html"],
      ["approvals", "승인", "check", "../manager/approvals.html"],
      ["staff", "직원", "users", "../manager/staff.html"],
    ],
    owner: [
      ["stores", "매장", "store", "../owner/stores.html"],
      ["schedule", "근무표", "calendar", "../manager/schedule-bulk.html?role=owner"],
      ["open", "급구", "plus", "../owner/open-shift.html"],
      ["approvals", "승인", "check", "../manager/approvals.html?role=owner"],
      ["settings", "설정", "gear", "../owner/settings.html"],
    ],
  };

  const params = new URLSearchParams(location.search);

  // 점장 화면을 사장이 같이 쓸 때 (?role=owner) 헤더 계정 정보를 사장으로
  const isOwnerView = params.get("role") === "owner" && document.querySelector("nav.tabbar[data-role-param]");
  if (isOwnerView) {
    document.querySelectorAll(".account-meta").forEach((meta) => {
      meta.innerHTML = "<b>김효주 사장님</b><small>2개 매장 운영</small>";
    });
    document.title = document.title.replace("점장", "사장");
  }

  document.querySelectorAll("nav.tabbar[data-role]").forEach((nav) => {
    const role = ("roleParam" in nav.dataset && params.get("role")) || nav.dataset.role;
    const counts = Object.fromEntries(
      (nav.dataset.counts || "").split(",").filter(Boolean).map((p) => p.split(":"))
    );
    const tabs = TABS[role];
    nav.style.gridTemplateColumns = `repeat(${tabs.length}, 1fr)`;
    nav.setAttribute("aria-label", "메뉴");
    nav.innerHTML = tabs.map(([key, label, icon, href]) => `
      <a href="${href}" class="${key === nav.dataset.active ? "active" : ""}" ${key === nav.dataset.active ? 'aria-current="page"' : ""}>
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${ICON[icon]}</svg>
        ${label}${counts[key] ? `<span class="count" data-count-for="${key}">${counts[key]}</span>` : ""}
      </a>`).join("");
  });

  // ---------- API 표시 (기본 숨김) ----------
  // 발표 캡처할 때 화면 제목을 3번 누르면 켜고 꺼져요.
  const API_KEY = "show-api";
  // 발표 캡처용: 주소 끝에 ?api=1 을 붙여도 켜져요
  try {
    if (params.get("flat") === "1") document.body.classList.add("flat");
  // 캡처용: ?scroll=800 이면 그만큼 내려서 찍어요
  if (params.get("scroll")) addEventListener("load", () => scrollTo(0, Number(params.get("scroll"))));
    if (params.get("api") === "1" || localStorage.getItem(API_KEY) === "1") document.body.classList.add("show-api");
  } catch (e) {
    if (params.get("api") === "1") document.body.classList.add("show-api");
  }

  let taps = 0;
  let tapTimer;
  document.addEventListener("click", (e) => {
    if (e.target.closest(".topbar h1")) {
      taps += 1;
      clearTimeout(tapTimer);
      tapTimer = setTimeout(() => (taps = 0), 600);
      if (taps === 3) {
        taps = 0;
        const on = document.body.classList.toggle("show-api");
        try { localStorage.setItem(API_KEY, on ? "1" : "0"); } catch (err) { /* 무시 */ }
        toast(on ? "API 표시를 켰어요" : "API 표시를 껐어요");
      }
    }

    const todo = e.target.closest("[data-todo]");
    if (todo) {
      e.preventDefault();
      toast(todo.dataset.todo);
    }
  });

  // ---------- 토스트 ----------
  window.toast = function (message) {
    let box = document.querySelector(".toast");
    if (!box) {
      box = document.createElement("div");
      box.className = "toast";
      box.setAttribute("role", "status");
      document.body.appendChild(box);
    }
    box.textContent = message;
    box.classList.add("show");
    clearTimeout(box._timer);
    box._timer = setTimeout(() => box.classList.remove("show"), 2400);
  };

  // ---------- 유틸 ----------
  const WEEKDAYS = ["일", "월", "화", "수", "목", "금", "토"];
  window.WEEKDAYS = WEEKDAYS;
  window.TODAY = "2026-09-15";
  window.params = params;
  window.pad = (n) => String(n).padStart(2, "0");
  window.toISO = (y, m, d) => `${y}-${pad(m)}-${pad(d)}`;

  window.fmtDate = function (iso) {
    const [y, m, d] = iso.split("-").map(Number);
    return `${m}/${d}(${WEEKDAYS[new Date(y, m - 1, d).getDay()]})`;
  };

  window.hoursBetween = function (start, end) {
    const [sh, sm] = start.split(":").map(Number);
    const [eh, em] = end.split(":").map(Number);
    return (eh * 60 + em - (sh * 60 + sm)) / 60;
  };

  window.daysBetween = function (fromISO, toISO) {
    const [fy, fm, fd] = fromISO.split("-").map(Number);
    const [ty, tm, td] = toISO.split("-").map(Number);
    return Math.round((new Date(ty, tm - 1, td) - new Date(fy, fm - 1, fd)) / 86400000);
  };

  // 휴대폰 번호 자동 하이픈
  window.formatPhone = function (value) {
    const d = value.replace(/\D/g, "").slice(0, 11);
    if (d.length < 4) return d;
    if (d.length < 8) return `${d.slice(0, 3)}-${d.slice(3)}`;
    return `${d.slice(0, 3)}-${d.slice(3, 7)}-${d.slice(7)}`;
  };

  // 시간대(오픈·미들·마감)마다 다른 색. .pill b0/b1/b2 와 .band-0/1/2 로 쓰여요
  const BANDS_ORDER = ["오픈", "미들", "마감"];
  window.bandNo = (name) => Math.max(0, BANDS_ORDER.indexOf(name)) % 3;

  // 아바타에 넣을 이름 (세 글자 이름은 성을 빼고 두 글자)
  window.shortName = (name) => (!name ? "?" : name.length >= 3 ? name.slice(1) : name);

  // 사장이 점장 화면을 볼 때 매장 전환 버튼 (<div class="segmented" id="storeSwitch" hidden></div>)
  window.OWNER_STORE_NAMES = { A: "성수점", B: "건대점" };
  window.mountStoreSwitch = function (onChange) {
    let key = params.get("store") === "B" ? "B" : "A";
    const box = document.getElementById("storeSwitch");
    if (!box || params.get("role") !== "owner") return key;
    box.hidden = false;
    box.setAttribute("role", "group");
    box.setAttribute("aria-label", "매장 선택");
    const draw = () => {
      box.innerHTML = Object.entries(window.OWNER_STORE_NAMES).map(([k, n]) =>
        `<button type="button" data-store-key="${k}" aria-pressed="${k === key}">${n}</button>`).join("");
    };
    draw();
    box.addEventListener("click", (e) => {
      const b = e.target.closest("[data-store-key]");
      if (!b || b.dataset.storeKey === key) return;
      key = b.dataset.storeKey;
      draw();
      onChange(key);
    });
    return key;
  };

  window.escapeHTML = (s) =>
    String(s).replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));

  // ---------- 근무표 전달사항 ----------
  // 점장 근무표와 직원 캘린더가 같은 내용을 읽어요. 실제 서비스에서는 매장 공지 API로 대체하면 돼요.
  const NOTICE_DEFAULTS = {
    A: [
      { id: "a-0916", noticeDate: "2026-09-16", content: "오후 2시 재료 입고 예정이에요. 냉동고 앞을 비워주세요.", createdByName: "김계원" },
      { id: "a-0925", noticeDate: "2026-09-25", content: "오전 위생 점검이 있어요. 유니폼과 명찰을 확인해주세요.", createdByName: "김계원" },
      { id: "a-1010", noticeDate: "2026-10-10", content: "신입 직원 첫 출근이에요. 오픈 담당이 인수인계 부탁해요.", createdByName: "김계원" },
      { id: "a-1017", noticeDate: "2026-10-17", content: "냉동고 정기 점검 예정이에요. 마감 전에 재고를 정리해주세요.", createdByName: "김계원" },
    ],
    B: [
      { id: "b-0917", noticeDate: "2026-09-17", content: "포스기 업데이트가 있어요. 오픈 담당은 30분 일찍 와주세요.", createdByName: "김효주" },
      { id: "b-0919", noticeDate: "2026-09-19", content: "주말 행사 물량이 들어와요. 창고 통로를 비워주세요.", createdByName: "김효주" },
      { id: "b-0926", noticeDate: "2026-09-26", content: "신메뉴 교육 영상 시청 부탁해요. 마감 전에 10분이면 돼요.", createdByName: "김효주" },
      { id: "b-1008", noticeDate: "2026-10-08", content: "오전 소방 점검 예정입니다.", createdByName: "김효주" },
    ],
  };
  const noticeKey = (storeKey) => `store-notices-v3:${storeKey}`;
  window.getStoreNotices = function (storeKey) {
    try {
      const saved = localStorage.getItem(noticeKey(storeKey));
      if (saved) return JSON.parse(saved);
      const initial = (NOTICE_DEFAULTS[storeKey] || []).map((n) => ({ ...n }));
      localStorage.setItem(noticeKey(storeKey), JSON.stringify(initial));
      return initial;
    } catch (e) {
      return (NOTICE_DEFAULTS[storeKey] || []).map((n) => ({ ...n }));
    }
  };
  window.saveStoreNotices = function (storeKey, notices) {
    try { localStorage.setItem(noticeKey(storeKey), JSON.stringify(notices)); } catch (e) { /* 저장소 사용 불가 시 화면에서만 유지 */ }
  };

  // ---------- 포트폴리오 데모 상태 ----------
  // 요청 등록 → 내 활동 → 내 근무가 한 흐름으로 이어지는 것을 보여주기 위한 브라우저 저장값이에요.
  const WORKER_REQUEST_KEY = "demo-worker-requests-v1";
  window.getDemoWorkerRequests = function () {
    try { return JSON.parse(localStorage.getItem(WORKER_REQUEST_KEY) || "[]"); }
    catch (e) { return []; }
  };
  window.addDemoWorkerRequest = function (request) {
    const requests = getDemoWorkerRequests();
    requests.unshift(request);
    try { localStorage.setItem(WORKER_REQUEST_KEY, JSON.stringify(requests)); } catch (e) { /* 저장소 사용 불가 시 현재 화면만 유지 */ }
    return request;
  };

  // 공통 뒤로가기 아이콘
  window.BACK_ICON = '<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M15 18l-6-6 6-6"/></svg>';
})();
