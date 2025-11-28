package edu.univ.erp.domain;

public class ServiceResult<T> {
    private final boolean success;
    private final String message;
    private final T data;

    private ServiceResult(boolean success, String message, T data) {
        this.success = success;
        this.message = message;
        this.data = data;
    }

    public static <T> ServiceResult<T> success(String message, T data) {
        return new ServiceResult<>(true, message, data);
    }

    public static <T> ServiceResult<T> success(String message) {
        return new ServiceResult<>(true, message, null);
    }

    public static <T> ServiceResult<T> error(String message) {
        return new ServiceResult<>(false, message, null);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }

    public T getData() {
        return data;
    }

    @Override
    public String toString() {
        return "ServiceResult{" +
                "success=" + success +
                ", message='" + message + '\'' +
                ", data=" + data +
                '}';
    }
}