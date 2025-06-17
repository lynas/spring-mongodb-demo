package com.lynas.mongo

import org.springframework.boot.fromApplication
import org.springframework.boot.with


fun main(args: Array<String>) {
	fromApplication<MongoSpringApplication>().with(TestcontainersConfiguration::class).run(*args)
}
