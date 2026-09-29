# 배포 가이드

대타구함은 프론트엔드와 백엔드를 나눠 배포합니다.

- 프론트엔드: 기존 GitHub Pages (`https://oojoyhh.github.io/daetaguham/`)
- 백엔드·PostgreSQL: Render Blueprint (`render.yaml`)

## 1. 변경사항 푸시

`render.yaml`과 `backend/Dockerfile`을 포함한 커밋을 기본 브랜치에 푸시합니다.

## 2. Render Blueprint 생성

1. Render 대시보드에서 **New → Blueprint**를 선택합니다.
2. GitHub의 `oojoyhh/daetaguham` 저장소를 연결합니다.
3. 저장소 루트의 `render.yaml`을 선택합니다.
4. 생성될 웹 서비스와 PostgreSQL을 확인하고 배포를 시작합니다.

Blueprint가 다음 항목을 자동으로 설정합니다.

- Java 21 Docker 이미지 빌드
- PostgreSQL 17 생성 및 비공개 네트워크 연결
- 무작위 JWT 비밀키 생성
- GitHub Pages 출처 CORS 허용
- `/api/actuator/health` 헬스체크
- Flyway V1~V7 자동 마이그레이션

## 3. 배포 확인과 프론트 연결

Render가 실제로 발급한 웹 서비스 주소 뒤에 `/api/actuator/health`를 붙여 `UP`인지 확인합니다.
주소를 확인하기 전에는 로그인 정보가 엉뚱한 서버로 전달되지 않도록 프론트엔드에 임의 주소를 넣지 않습니다.

확인이 끝나면 `web/js/common.js`의 `DEPLOYED_API_BASE`에 다음 형식으로 실제 주소를 입력합니다.

```js
const DEPLOYED_API_BASE = "https://실제-서비스-주소.onrender.com/api";
```

이 변경을 다시 푸시해 GitHub Pages가 갱신되면 회원가입과 로그인부터 실제 서버 모드로 동작합니다.

## 4. 포트폴리오 운영 시 주의

Render 무료 PostgreSQL은 장기 보관용이 아닙니다. 무료 DB 만료 전에 유료 플랜으로 전환하거나
별도의 장기 운영 PostgreSQL로 옮겨야 포트폴리오 데이터가 계속 유지됩니다.
테스트 계정 비밀번호에는 실제 사용하는 비밀번호를 재사용하지 않습니다.
