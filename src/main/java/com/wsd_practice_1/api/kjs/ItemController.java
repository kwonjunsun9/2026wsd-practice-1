package com.wsd_practice_1.api.kjs;

import com.wsd_practice_1.api.dto.ItemDto;
import com.wsd_practice_1.api.request.ItemCreateRequest;
import com.wsd_practice_1.api.request.ItemUpdateRequest;
import com.wsd_practice_1.api.response.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
/**
 * - 응답 형식: 모든 응답은 ApiResponse(success / fail / error)로 통일합니다.
 *     success -> 2xx 성공
 *     fail    -> 4xx 클라이언트 요청 오류
 *     error   -> 5xx 서버 오류
 * - 사용한 응답 코드: 200, 201, 202 / 400, 404 / 500, 503
 */

@RestController
@RequestMapping("/api/kjs/items") // 아래 모든 API 주소 앞에 공통으로 붙는 기본 경로
public class ItemController {

    private final Map<Long, ItemDto> store = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong(1L);

    // 1) POST : 단건 생성
    // 요청: POST /api/kjs/items  / Body: {"name":"a","price":100}
    // 응답: 201(생성 성공), 400(이름/가격 오류), 500(name이 "server-error"일 때)
    @PostMapping
    public ResponseEntity<ApiResponse> createItem(@RequestBody ItemCreateRequest request) {
        //이름이 비었거나 가격이 없거나 음수면 400 반환
        if (!isValidCreateRequest(request)) {
            return badRequest("400 Bad Request, 이름과 가격이 제대로 입력되지 않았습니다.");
        }
        // 이름이 "server-error"이면 500 반환
        if ("server-error".equalsIgnoreCase(request.getName())) {
            return serverError("500 Server Error. 예상치 못한 오류가 발생하였습니다.");

        }

        ItemDto item = createAndSaveItem(request);
        //201 Created
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(item));
    }

    // 2) POST : 여러개 생성
    // 요청: POST /api/kjs/items/many  / Body: [{"name":"a","price":100},{"name":"b","price":200}]
    // 응답: 201(생성 성공), 400(비었거나 항목 오류), 503(6개 이상일 때)
    @PostMapping("/many")
    public ResponseEntity<ApiResponse> createItems(@RequestBody List<ItemCreateRequest> requests) {
        //배열이 null이거나 비어 있으면 400 반환
        if (requests == null || requests.isEmpty()) {
            return badRequest("400 Bad Request, 최소 한 개 이상의 아이템이 필요합니다.");
        }

        // 6개 이상이면 503 반환
        if (requests.size() > 5) {
            return serviceUnavailable("503 Service Unavailable, 6개 이상은 입력할 수 없습니다.");
        }

        // 저장하기 전에 모든 항목을 먼저 검사한다.
        // 하나라도 잘못되면 아무것도 저장하지 않고 400을 반환 (일부만 저장되는 상황 방지)
        for (ItemCreateRequest request : requests) {
            if (!isValidCreateRequest(request)) {
                return badRequest("400 Bad Request, 모든 아이템들의 이름과 가격을 다시 한번 확인해 주세요.");
            }
        }

        List<ItemDto> createdItems = requests.stream()
                .map(this::createAndSaveItem)
                .toList();
        // 201 Created
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(createdItems));
    }

    // 1) GET: 목록 전체 조회
    // 요청: GET /api/kjs/items  또는  GET /api/kjs/items?keyword=key
    // 응답: 200
    @GetMapping
    public ResponseEntity<ApiResponse> getItems(@RequestParam(required = false) String keyword) {
        List<ItemDto> items = store.values().stream()
                .filter(item -> keyword == null || item.getName().contains(keyword))
                .sorted(Comparator.comparing(ItemDto::getId))
                .toList();
        // 200 OK
        return ResponseEntity.ok(ApiResponse.success(items));
    }

    // 2) GET: 단건 조회 (ID)
    // 요청: GET /api/kjs/items/1
    // 응답: 200, 400(id가 0 이하), 404(해당 id 없음)
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse> getItem(@PathVariable Long id) {
        // id가 null이거나 0 이하이면 400 반환
        if (!isPositiveId(id)) {
            return badRequest("400 Bad Request. 아이디는 0보다 큰 숫자로 구성되어야 합니다.");
        }

        ItemDto item = store.get(id);
        //아이템이 없으면 404 반환
        if (item == null) {
            return notFound("404 Not Found. 아이템을 찾을 수 없습니다.");
        }
        // 200 OK
        return ResponseEntity.ok(ApiResponse.success(item));
    }

    // 1) PUT: 이름, 가격 수정
    // 요청: PUT /api/kjs/items/1  / Body: {"name":"c","price":35000}
    // 응답: 200, 400(id 또는 Body 오류), 404(해당 id 없음)
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse> updateItem(
            @PathVariable Long id,
            @RequestBody ItemUpdateRequest request
    ) {
        // id가 null이거나 0 이하이면 400 반환
        if (!isPositiveId(id)) {
            return badRequest("400 Bad Request. 아이디는 0보다 큰 숫자로 구성되어야 합니다.");
        }
        //이름이 비었거나 가격이 없거나 음수면 400 반환
        if (!isValidUpdateRequest(request)) {
            return badRequest("400 Bad Request, 이름과 가격이 제대로 입력되지 않았습니다.");
        }

        ItemDto item = store.get(id);
        //아이템이 없으면 404 반환
        if (item == null) {
            return notFound("404 Not Found. 아이템을 찾을 수 없습니다.");
        }

        if (request.getName() != null && !request.getName().isBlank()) {
            item.setName(request.getName());
        }
        if (request.getPrice() != null) {
            item.setPrice(request.getPrice());
        }

        return ResponseEntity.ok(ApiResponse.success(item)); // 200 OK
    }

    // 2) PUT: 가격 할인
    // 요청: PUT /api/kjs/items/1/discount?percent=10
    // 응답: 200, 400(id 또는 percent 오류), 404(해당 id 없음)
    @PutMapping("/{id}/discount")
    public ResponseEntity<ApiResponse> discountItem(
            @PathVariable Long id,
            @RequestParam(required = false) Integer percent
    ) {
        // id가 null이거나 0 이하이면 400 반환
        if (!isPositiveId(id)) {
            return badRequest("400 Bad Request. 아이디는 0보다 큰 숫자로 구성되어야 합니다.");
        }
        // percent가 없거나 1~99 범위를 벗어나면 400 반환
        if (percent == null || percent < 1 || percent > 99) {
            return badRequest("400 Bad Request. 할인률은 1% ~ 99% 사이여야 합니다.");
        }

        ItemDto item = store.get(id);
        //아이템이 없으면 404 반환
        if (item == null) {
            return notFound("404 Not Found. 아이템을 찾을 수 없습니다.");
        }

        int discountedPrice = item.getPrice() * (100 - percent) / 100;
        item.setPrice(discountedPrice);

        return ResponseEntity.ok(ApiResponse.success(item)); // 200 OK
    }

    // 1) DELETE: 단건 삭제
    // 요청: DELETE /api/kjs/items/1
    // 응답: 200, 400(id가 0 이하), 404(해당 id 없음)
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse> deleteItem(@PathVariable Long id) {
        // id가 null이거나 0 이하이면 400 반환
        if (!isPositiveId(id)) {
            return badRequest("400 Bad Request. 아이디는 0보다 큰 숫자로 구성되어야 합니다.");
        }

        ItemDto removedItem = store.remove(id);
        //아이템이 없으면 404 반환
        if (removedItem == null) {
            return notFound("404 Not Found. 아이템을 찾을 수 없습니다.");
        }

        return ResponseEntity.ok(ApiResponse.success(Map.of("deletedId", id))); // 200 OK
    }

    // 2) DELETE: 전체 삭제
    // 요청: DELETE /api/kjs/items
    // 응답: 202 (테스트 시 다른 데이터가 모두 사라지므로 맨 마지막에 실행)
    @DeleteMapping
    public ResponseEntity<ApiResponse> deleteItems() {
        int deletedCount = store.size();
        store.clear();

        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success(Map.of("deletedCount", deletedCount))); // 202 Accepted
    }

    //상품 생성 후 저장
    private ItemDto createAndSaveItem(ItemCreateRequest request) {
        ItemDto item = new ItemDto();
        item.setId(sequence.getAndIncrement());
        item.setName(request.getName());
        item.setPrice(request.getPrice());
        store.put(item.getId(), item);
        return item;
    }

    //생성 요청 검증
    private boolean isValidCreateRequest(ItemCreateRequest request) {
        return request != null
                && request.getName() != null
                && !request.getName().isBlank()
                && request.getPrice() != null
                && request.getPrice() >= 0;
    }

    //수정 요청 검증
    private boolean isValidUpdateRequest(ItemUpdateRequest request) {
        if (request == null) {
            return false;
        }

        boolean hasName = request.getName() != null && !request.getName().isBlank();
        boolean hasPrice = request.getPrice() != null && request.getPrice() >= 0;
        boolean invalidPrice = request.getPrice() != null && request.getPrice() < 0;

        return !invalidPrice && (hasName || hasPrice);
    }

    // Fail - 클라이언트 요청 문제 (4xx), Error - 서버문제 (5xx)
    //ID 검증
    private boolean isPositiveId(Long id) {
        return id != null && id > 0;
    }

    // 400 Bad Request
    private ResponseEntity<ApiResponse> badRequest(String message) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.fail(message));
    }

    // 404 Not Found
    private ResponseEntity<ApiResponse> notFound(String message) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.fail(message));
    }

    // 500 Sever Error
    private ResponseEntity<ApiResponse> serverError(String message) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error(message));
    }

    // 503 Service Unavailable
    private ResponseEntity<ApiResponse> serviceUnavailable(String message) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ApiResponse.error(message));
    }
}
