package rest_api.exception;

import org.springframework.http.HttpStatus;

public class BadRequestException extends GeneralException {
    public BadRequestException(String reason) {
        super(HttpStatus.BAD_REQUEST, reason); // 400
    }
}
