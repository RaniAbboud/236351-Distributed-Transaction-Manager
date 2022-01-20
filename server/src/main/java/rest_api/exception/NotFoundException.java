package rest_api.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

public class NotFoundException extends GeneralException {
    public NotFoundException(String reason) {
        super(HttpStatus.NOT_FOUND, reason); // 404
    }
}
