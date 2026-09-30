const FRONTEND_URL = process.env.FRONTEND_URL || "https://oojoyhh.github.io/daetaguham/";
const API_BASE_URL = process.env.API_BASE_URL || "https://daetaguham-api-oojoyhh.onrender.com/api";
const DEMO_PASSWORD = process.env.DEMO_PASSWORD || "demo1234!";
const REQUEST_TIMEOUT_MS = 195_000;

function assert(condition, message) {
  if (!condition) throw new Error(message);
}

async function request(url, options = {}) {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS);
  try {
    const response = await fetch(url, { ...options, signal: controller.signal });
    const body = await response.text();
    if (!response.ok) {
      throw new Error(`${response.status} ${response.statusText}: ${body.slice(0, 300)}`);
    }
    return body;
  } finally {
    clearTimeout(timeout);
  }
}

async function json(path, options = {}) {
  const body = await request(`${API_BASE_URL}${path}`, options);
  return JSON.parse(body);
}

async function login(phone) {
  return json("/auth/login", {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ phone, password: DEMO_PASSWORD }),
  });
}

function authorization(token) {
  return { authorization: `Bearer ${token}` };
}

function dateRange() {
  const today = new Date();
  const from = new Date(today);
  const to = new Date(today);
  from.setUTCDate(from.getUTCDate() - 45);
  to.setUTCDate(to.getUTCDate() + 45);
  return {
    from: from.toISOString().slice(0, 10),
    to: to.toISOString().slice(0, 10),
  };
}

console.log("1/5 프론트엔드 확인");
const frontend = await request(FRONTEND_URL);
assert(frontend.includes("<title>대타구함 | 교대 관리 서비스</title>"), "프론트엔드 제목을 찾지 못했습니다.");

console.log("2/5 백엔드와 DB 상태 확인");
const health = await json("/actuator/health");
assert(health.status === "UP", `백엔드 상태가 UP이 아닙니다: ${health.status}`);

console.log("3/5 사장 로그인과 매장 현황 확인");
const owner = await login("010-9000-0001");
assert(owner.user?.name === "김효주", "사장 데모 계정 정보가 예상과 다릅니다.");
const ownerStore = owner.stores?.find((store) => store.myRole === "OWNER");
assert(ownerStore?.storeId, "사장 소속 매장을 찾지 못했습니다.");
const summary = await json(`/stores/${ownerStore.storeId}/summary`, {
  headers: authorization(owner.token),
});
assert(Number.isInteger(summary.todayWorkerCount), "매장 현황 응답이 올바르지 않습니다.");

console.log("4/5 알바생 로그인과 계정 확인");
const worker = await login("010-9000-0003");
assert(worker.user?.name === "이진호", "알바생 데모 계정 정보가 예상과 다릅니다.");
const me = await json("/me", { headers: authorization(worker.token) });
assert(me.user?.name === "이진호", "인증된 내 정보 조회가 올바르지 않습니다.");

console.log("5/5 알바생 근무 조회 확인");
const range = dateRange();
const shifts = await json(`/me/shifts?from=${range.from}&to=${range.to}`, {
  headers: authorization(worker.token),
});
assert(Array.isArray(shifts) && shifts.length > 0, "데모 알바생의 근무를 찾지 못했습니다.");

console.log(`배포 스모크 테스트 통과 · 매장 ${ownerStore.storeName} · 근무 ${shifts.length}건`);
