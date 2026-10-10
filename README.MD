# 2026 웹서비스 설계 실습 1) CRUD 백엔드 만들기

Spring Boot로 상품(Item) 관리 API를 구현한 실습 과제입니다.
<br />HTTP 메소드별 API 2개씩(총 8개), 미들웨어(Middleware) 구현 ,응답 코드 다양성, 통일된 응답 포맷을 구현했습니다.
<br />전반적인 실습 코드는 'https://github.com/JBNU-KSL-25-02/springboot-tutorial.git' 을 참고하여 완성하였습니다.

## 1. 개발 환경

- Java: [JDK 21]
- Spring Boot: [3.5.7]
- 테스트 도구: Postman
- 저장소: DB 없이 메모리에 저장 (서버를 재시작하면 데이터와 id 번호가 초기화됨)

## 2. 실행 방법 (Window 기준으로 실행)

```bash
.\gradlew clean build
```
```bash
java -jar build/libs/wsd-practice-1-0.0.1-SNAPSHOT.jar
```

- 기본 주소: `http://localhost:8080`
- API 기본 경로: `/api/kjs/items`

## 3. 프로젝트 구조

```
2026wsd-practice-1\src\main\java\com\wsd_practice_1\api\kjs
├── ItemController.java        # 8개 API 구현
├── ExceptionHandler.java      # 컨트롤러 이전 단계의 예외를 통일된 응답으로 변환
└── RequestLoggingFilter.java  # 요청 로그 출력 미들웨어
```

## 4. API 목록 (총 8개)

| # | 메소드 | 경로 | 설명 | 응답 코드 |
|---|--------|------|------|-----------|
| 1 | POST | `/api/kjs/items` | 상품 단건 생성 | 201, 400, 500 |
| 2 | POST | `/api/kjs/items/many` | 상품 여러 개 생성 (최대 5개) | 201, 400, 503 |
| 3 | GET | `/api/kjs/items` | 전체 조회 (`?keyword=`로 이름 검색 가능) | 200 |
| 4 | GET | `/api/kjs/items/{id}` | 단건 조회 | 200, 400, 404 |
| 5 | PUT | `/api/kjs/items/{id}` | 이름/가격 수정 | 200, 400, 404 |
| 6 | PUT | `/api/kjs/items/{id}/discount?percent=` | 가격 할인 (1~99%) | 200, 400, 404 |
| 7 | DELETE | `/api/kjs/items/{id}` | 단건 삭제 | 200, 400, 404 |
| 8 | DELETE | `/api/kjs/items` | 전체 삭제 | 202 |

### 요청 예시

```http
POST /api/kjs/items
{"name": "keyboard", "price": 30000}

POST /api/kjs/items/many
[{"name": "a", "price": 100}, {"name": "b", "price": 200}]

PUT /api/kjs/items/1
{"name": "keyboard-v2", "price": 35000}

PUT /api/kjs/items/1/discount?percent=10
```

## 5. 미들웨어 (Middleware)

`RequestLoggingFilter`는 모든 요청의 HTTP 메소드, URI, 응답 상태 코드, 처리 시간을 콘솔에 출력합니다.

```
[HTTP] POST /api/kjs/items | Status: 201 | Duration: 3 ms
```

## 6. 사용한 응답 코드

| 구분 | 코드 | 사용 위치 / 재현 방법 |
|------|------|------------------------|
| 2xx | 200 OK | 조회, 수정, 할인, 단건 삭제 성공 |
| 2xx | 201 Created | 상품 생성 성공 |
| 2xx | 202 Accepted | 전체 삭제 |
| 4xx | 400 Bad Request | 이름/가격 오류, id가 0 이하, 할인율 범위 오류, `/items/abc` |
| 4xx | 404 Not Found | 존재하지 않는 id 조회/수정/삭제/할인 |
| 5xx | 500 Internal Server Error | `POST /items`에 `name`을 `server-error`로 전송 |
| 5xx | 503 Service Unavailable | `POST /items/many`에 6개 이상 전송 |

> 500과 503은 5xx 응답을 시연하기 위해 의도적으로 만든 조건입니다.
> 202는 응답 코드 다양성을 위해 전체 삭제에 사용했습니다.

## 7. 응답 포맷

수업 자료의 표준 포맷에 맞춰 모든 응답을 `ApiResponse`로 통일했습니다.

| status | 의미 | HTTP 코드 |
|--------|------|-----------|
| `success` | 요청 성공 | 2xx |
| `fail` | 클라이언트 요청 오류 | 4xx |
| `error` | 서버 오류 | 5xx |

성공 예시 (201 Created)
```json
{
  "status": "success",
  "data": { "id": 1, "name": "keyboard", "price": 30000 }
}
```

실패 예시 (404 Not Found)
```json
{
  "status": "fail",
  "message": "404 Not Found. 아이템을 찾을 수 없습니다."
}
```

서버 오류 예시 (500)
```json
{
  "status": "error",
  "message": "500 Server Error. 예상치 못한 오류가 발생하였습니다."
}
```

## 8. 테스트 결과 (Postman)

모든 응답 코드와 콘솔 로그를 캡처했습니다. LMS와 Git에 **웹서비스 설계 실습1 - 202219384 권준성.pdf** 라는 파일명으로 업로드하였습니다.
