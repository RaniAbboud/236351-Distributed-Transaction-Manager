package rest_api.controller

import model.Transaction
import rest_api.service.TransactionService
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody

/**
 * Controller for REST API endpoints
 */
@RestController
class TransactionController(private val transactionService: TransactionService) {

    @GetMapping("/transactions")
    fun getAllTransactions(): List<Transaction> = transactionService.getAllTransactions()

    @GetMapping("/users/{id}/transactions")
    fun getAllTransactionsForUser(@PathVariable("id") userId: Long): Transaction =
            transactionService.getTransactionsForUser(userId)

    @PostMapping("/transactions")
    fun createTransaction(@RequestBody payload: Transaction): Transaction = transactionService.createTransaction(payload)
}