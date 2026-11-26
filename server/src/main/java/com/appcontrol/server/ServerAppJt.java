package com.appcontrol.server;

import java.time.Clock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/** API de AppControl en la nube (Azure). Las PC consultan por HTTPS; nunca se conectan a la base. */
@SpringBootApplication
@EnableJpaRepositories(considerNestedRepositories = true)
public class ServerAppJt {

    public static void main(String[] argsJt) {
        SpringApplication.run(ServerAppJt.class, argsJt);
    }

    /** Reloj del servidor; las pruebas lo reemplazan para simular el paso del tiempo. */
    @Bean
    Clock clockMa() {
        return Clock.systemUTC();
    }
}
