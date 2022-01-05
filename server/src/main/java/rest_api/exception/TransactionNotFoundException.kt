package rest_api.exception

import org.springframework.http.HttpStatus

class TransactionNotFoundException(val statusCode: HttpStatus, val reason: String) : Exception()