package rest_api;

import constants.Constants;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

import java.util.Arrays;
import java.util.Collections;

@SpringBootApplication
public class RestServer {

    public static void main(String[] args) {
        org.apache.log4j.BasicConfigurator.configure();
        SpringApplication app = new SpringApplication(RestServer.class);
        app.setDefaultProperties(Collections.singletonMap("server.port", System.getenv(Constants.ENV_REST_PORT)));
        app.run(args);
    }

    @Bean
    public CommandLineRunner commandLineRunner(ApplicationContext ctx) {
        return args -> {

            System.out.println("Let's inspect the beans provided by Spring Boot:");

            String[] beanNames = ctx.getBeanDefinitionNames();
            Arrays.sort(beanNames);
            for (String beanName : beanNames) {
                System.out.println(beanName);
            }

        };
    }

}
