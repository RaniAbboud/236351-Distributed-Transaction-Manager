package rest_api.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

public class ConflictException extends GeneralException {
    public ConflictException(String reason) {
        super(HttpStatus.CONFLICT, reason); // 409
    }
}
