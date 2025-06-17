package com.lynas.mongo

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.dao.DuplicateKeyException
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.aggregation.Aggregation
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@SpringBootApplication
class MongoSpringApplication

fun main(args: Array<String>) {
    runApplication<MongoSpringApplication>(*args)
}

@Document
data class Customer(
    @Id
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    @Indexed(unique = true)
    val email: String
) {
    object CustomerFields {
        const val TABLE = "customer"
        const val ID = "_id"
        const val NAME = "name"
        const val EMAIL = "email"
    }
}

@Document("orders")
data class Order(
    @Id val id: String = UUID.randomUUID().toString(),
    val customerId: String,
    val amount: Double
)

interface CustomerRepository : MongoRepository<Customer, String>
interface OrderRepository : MongoRepository<Order, String>

data class CustomerOrderDTO(
    val customerName: String,
    val orderId: String,
    val orderAmount: Double
)

@Service
class CustomCustomerOrderService(
    val mongoTemplate: MongoTemplate,
) {
    fun findCustomerOrders(): List<CustomerOrderDTO> {
        val aggregation = Aggregation.newAggregation(
            Aggregation.lookup("orders", "_id", "customerId", "orders"),
            Aggregation.unwind("orders"),
            Aggregation.project()
                .and("name").`as`("customerName")
                .and("orders._id").`as`("orderId")
                .and("orders.amount").`as`("orderAmount")
        )

        val results = mongoTemplate.aggregate(aggregation, Customer.CustomerFields.TABLE, CustomerOrderDTO::class.java)
        return results.mappedResults
    }

}

@RestController
@RequestMapping("/customers")
class CustomerRestController(
    val customerRepository: CustomerRepository,
    val orderRepository: OrderRepository,
    val customCustomerOrderService: CustomCustomerOrderService
) {
    @PostMapping("")
    fun saveNewCustomer(@RequestBody customer: Customer): ResponseEntity<Any> {
        return try {
            ResponseEntity(
                customerRepository.save(customer.copy(id = UUID.randomUUID().toString())),
                HttpStatus.CREATED
            )
        } catch (e: DuplicateKeyException) {
            ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(mapOf("error" to "Customer with this email already exists"))
        }
    }

    @GetMapping("")
    fun getAllCustomers(): List<Customer> {
        return customerRepository.findAll()
    }

    @PutMapping("/{id}")
    fun updateCustomer(
        @PathVariable("id") id: UUID,
        @RequestBody customer: Customer
    ): ResponseEntity<Customer> {
        val dbCustomer = customerRepository.findByIdOrNull(id.toString())
        return if (dbCustomer == null) {
            ResponseEntity.notFound().build()
        } else {
            ResponseEntity(
                customerRepository.save(customer.copy(id = id.toString())),
                HttpStatus.OK
            )
        }
    }

    @GetMapping("/customerAndOrders")
    fun getCustomerAndOrders(): List<CustomerOrderDTO> {
        return customCustomerOrderService.findCustomerOrders()
    }

    @PostMapping("customerOrder")
    fun saveNewCustomer(@RequestBody order: Order): Order {
        return orderRepository.save(order)
    }

    @GetMapping("/orders")
    fun orders(): List<Order> {
        return orderRepository.findAll()
    }
}