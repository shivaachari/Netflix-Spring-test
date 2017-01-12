package com.netflix.recommendations;

import java.util.Set;

import javax.inject.Inject;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.client.circuitbreaker.EnableCircuitBreaker;
import org.springframework.cloud.netflix.eureka.EnableEurekaClient;
import org.springframework.cloud.netflix.eureka.EurekaStatusChangedEvent;
import org.springframework.cloud.netflix.feign.EnableFeignClients;
import org.springframework.cloud.netflix.feign.FeignClient;
import org.springframework.cloud.netflix.hystrix.EnableHystrix;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.common.collect.Sets;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixProperty;

@SpringBootApplication
@EnableEurekaClient
@EnableHystrix
@EnableCircuitBreaker
@EnableFeignClients
public class RecommendationsClient {
    public static void main(String[] args) {
        new SpringApplicationBuilder(RecommendationsClient.class).web(true).run(args);
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
    @EventListener
    public void onEurekaStatusDown(EurekaStatusChangedEvent event) {
        if(event.getStatus() == InstanceInfo.InstanceStatus.DOWN || event.getStatus() == InstanceInfo.InstanceStatus.OUT_OF_SERVICE) {
            System.out.println("Stop listening to queues and such...");
        }
    }
}

@FeignClient("recommendations")
interface ReccomendationRepository {
    @RequestMapping(method = RequestMethod.GET, value = "/api/recommendations/{user}")
    Set<Movie> findRecommendationsForUser(@PathVariable("user") String user);
}

@RestController
@RequestMapping("/api/recommendationsclient")
class RecommendationsClientController {
    @Autowired
    RestTemplate restTemplate;
    
    @Inject
    ReccomendationRepository recommendationRepository;

    @RequestMapping(value= "/{user}", produces = MediaType.APPLICATION_JSON_VALUE)
    @HystrixCommand(fallbackMethod="fallbackMethod", 
    		commandProperties = { 
    				@HystrixProperty(name="execution.isolation.thread.timeoutInMilliseconds", value="5000"),
    				@HystrixProperty(name = "execution.isolation.strategy", value = "THREAD")
    	})
	public Set<Movie> findRecommendationsForUser(@PathVariable String user) throws UserNotFoundException {
    	
    	RequestContextHolder.currentRequestAttributes();
        Set<Movie> movies = recommendationRepository.findRecommendationsForUser(user);
        
        return movies;
        
    }
    
    Set<Movie> defaultRecommendations = Sets.<Movie>newHashSet(new Movie("Default - ","default"), new Movie("Default - ", "default"));
    
    
    Set<Movie> fallbackMethod(String user) {
    	return defaultRecommendations;
    }
    
    
}

@JsonIgnoreProperties(ignoreUnknown = true)
class Movie {
	public Movie(){};
    

	public String getTitle() {
		return title;
	}
	public void setTitle(String title) {
		this.title = title;
	}

	String title;
	String hostName;
	public Movie(String title, String hostName) {
		super();
		this.title = title;
		this.hostName = hostName;
	}
	public String getHostName() {
		return hostName;
	}
	public void setHostName(String hostName) {
		this.hostName = hostName;
	}
	
	String memberHostName;
	public String getMemberHostName() {
		return memberHostName;
	}
	public void setMemberHostName(String memberHostName) {
		this.memberHostName = memberHostName;
	}
}

class UserNotFoundException extends Exception {}
