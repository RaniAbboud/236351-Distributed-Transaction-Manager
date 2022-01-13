package rest_api.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

//@ResponseStatus(value= HttpStatus.BAD_REQUEST, reason="Invalid Transaction")  // 400
@ResponseStatus(value= HttpStatus.BAD_REQUEST)  // 400
public class TransactionIllegalException extends RuntimeException {
    private String message = "Invalid Transaction.";

    public TransactionIllegalException(String message){
        super(message);
        this.message = message;
    }

    @Override
    public String getMessage(){
        return this.message;
    }
}

