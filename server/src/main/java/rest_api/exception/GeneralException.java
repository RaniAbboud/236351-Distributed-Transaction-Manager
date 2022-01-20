package rest_api.exception;

import io.grpc.StatusRuntimeException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.server.ResponseStatusException;

abstract public class GeneralException extends ResponseStatusException {

    public GeneralException(HttpStatus statusCode, String message) {
        super(statusCode, message);
    }

    @Override
    public synchronized Throwable fillInStackTrace() {
        return this;
    }

}


