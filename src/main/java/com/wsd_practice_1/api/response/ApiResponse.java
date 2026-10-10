package com.wsd_practice_1.api.response;

import lombok.Getter;
import java.util.Map;

@Getter
public class ApiResponse {
    private final String status;
    private final Object data;

    private ApiResponse(String status, Object data) {
        this.status = status;
        this.data = data;
    }

    public static ApiResponse success(Object data) {
        return new ApiResponse("success", data);
    }

    public static ApiResponse fail(String message) {
        return new ApiResponse("fail", Map.of("message", message));
    }

    public static ApiResponse error(String message) {
        return new ApiResponse("error", Map.of("message", message));
    }

}
