package com.netflix.recommendations;

import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.client.circuitbreaker.EnableCircuitBreaker;
import org.springframework.cloud.netflix.eureka.EnableEurekaClient;
import org.springframework.cloud.netflix.eureka.EurekaStatusChangedEvent;
import org.springframework.cloud.netflix.hystrix.EnableHystrix;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import com.netflix.appinfo.InstanceInfo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.common.collect.Sets;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixProperty;

@SpringBootApplication
@EnableEurekaClient
@EnableHystrix
@EnableCircuitBreaker
public class Recommendations {
    public static void main(String[] args) {
        new SpringApplicationBuilder(Recommendations.class).web(true).run(args);
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

@RestController
@RequestMapping("/api/recommendations")
class RecommendationsController {
    @Autowired
    RestTemplate restTemplate;

    Set<Movie> kidRecommendations = Sets.<Movie>newHashSet(new Movie("lion king"), new Movie("frozen"));
    Set<Movie> adultRecommendations = Sets.<Movie>newHashSet(new Movie("shawshank redemption"), new Movie("spring"));
    Set<Movie> defaultRecommendations = Sets.<Movie>newHashSet(new Movie("KungFu Panda"), new Movie("Inside Out"));
    
    @RequestMapping(value= "/{user}", produces = MediaType.APPLICATION_JSON_VALUE)
    @HystrixCommand(fallbackMethod="fallbackMethod", 
    		ignoreExceptions = UserNotFoundException.class,
    		commandProperties = { @HystrixProperty(name="execution.isolation.thread.timeoutInMilliseconds", value="5000")
    })
	public Set<Movie> findRecommendationsForUser(@PathVariable String user) throws UserNotFoundException {
        Member member = restTemplate.getForObject("http://localhost:8000/api/member/{user}", Member.class, user);
        if(member == null)
            throw new UserNotFoundException();
        System.out.println("Member name : "+ member.user + " age : "+ member.age);
        
        if (member.age < 17) {
        	System.out.println("kidRecommendations:"+kidRecommendations);
        	return kidRecommendations;
        }
        else {
        	System.out.println("adultRecommendations:"+adultRecommendations);
        	return adultRecommendations;
        }
        	
        
        //return member.age < 17 ? kidRecommendations : adultRecommendations;
    }
    
    
     Set<Movie> fallbackMethod(String user) {
    	return defaultRecommendations;
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
class Movie {
	public Movie(){};
    public Movie(String title) {
		this.title = title;
	}

	public String getTitle() {
		return title;
	}
	public void setTitle(String title) {
		this.title = title;
	}

	String title;
}

@JsonIgnoreProperties(ignoreUnknown = true)
class Member {
    String user;
    Integer age;
    public Member(){};
	public Member(String user, Integer age) {
		super();
		this.user = user;
		this.age = age;
	}
	public String getUser() {
		return user;
	}
	public void setUser(String user) {
		this.user = user;
	}
	public Integer getAge() {
		return age;
	}
	public void setAge(Integer age) {
		this.age = age;
	}
}

class UserNotFoundException extends Exception {}