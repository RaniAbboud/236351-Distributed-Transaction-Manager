package rest_api.service

import rest_api.exception.TransactionNotFoundException
import rest_api.repository.TransactionRepository
import model.Transaction
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service

/**
 * Service for interactions with transaction domain object
 */
@Service
class TransactionService(private val transactionRepository: TransactionRepository) {

    /**
     * Get all transactions list.
     *
     * @return the list
     */
    fun getAllTransactions(): List<Transaction> = transactionRepository.findAll()

    /**
     * Gets transactions by id.
     *
     * @param transactionId the transaction id
     * @return the transaction by id
     * @throws TransactionNotFoundException the transaction not found exception
     */
    fun getTransactionsForUser(transactionId: Long): Transaction = transactionRepository.findById(transactionId)
            .orElseThrow { TransactionNotFoundException(HttpStatus.NOT_FOUND, "No matching transaction was found") }

    /**
     * Create transaction.
     *
     * @param transaction the transaction
     * @return the transaction
     */
    fun createTransaction(transaction: Transaction): Transaction = transactionRepository.save(transaction)
}