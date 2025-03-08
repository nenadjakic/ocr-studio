package com.github.nenadjakic.ocr.studio

import org.modelmapper.ModelMapper
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.boot.task.SimpleAsyncTaskSchedulerBuilder
import org.springframework.context.annotation.Bean
import org.springframework.data.mongodb.config.EnableMongoAuditing
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories
import org.springframework.data.web.config.EnableSpringDataWebSupport
import org.springframework.scheduling.TaskScheduler
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.annotation.EnableScheduling


@SpringBootApplication
@EnableMongoRepositories
@EnableMongoAuditing
@EnableAsync
@EnableScheduling
@EnableSpringDataWebSupport(pageSerializationMode = EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO)
class Application : CommandLineRunner {

    @Bean
     fun modelMapper(): ModelMapper {
        val modelMapper = ModelMapper()

        return modelMapper
    }

    @Bean
    fun taskScheduler(): TaskScheduler {
        return SimpleAsyncTaskSchedulerBuilder()
            .threadNamePrefix("TaskScheduler-")
            .virtualThreads(true)
            .concurrencyLimit(4)
            .build()
    }

    override fun run(vararg args: String?) {

    }
}

fun main(args: Array<String>) {
    runApplication<Application>(*args)
}