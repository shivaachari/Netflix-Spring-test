package com.netflix.recommendations;

import java.net.InetAddress;
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

@FeignClient("membership")
interface MembershipRepository {
    @RequestMapping(method = RequestMethod.GET, value = "/api/member/{user}")
    Member findMember(@PathVariable("user") String user);
}

@RestController
@RequestMapping("/api/recommendations")
class RecommendationsController {
    @Autowired
    RestTemplate restTemplate;
    
    static String hostName;
	static{
	try{
	 hostName = InetAddress.getLocalHost().getHostName();
	}catch(Exception ex) {hostName = "Host"+Math.random()*3;}
	}
    
    @Inject
    MembershipRepository membershipRepository;

    Set<Movie> kidRecommendations = Sets.<Movie>newHashSet(new Movie("Kid - lion king", hostName), new Movie("Kid - frozen", hostName));
    Set<Movie> adultRecommendations = Sets.<Movie>newHashSet(new Movie("Adult - shawshank redemption", hostName), new Movie("Adult - spring", hostName));
    Set<Movie> defaultRecommendations = Sets.<Movie>newHashSet(new Movie("Default - KungFu Panda", hostName), new Movie("Default - Inside Out", hostName));
    
    @RequestMapping(value= "/{user}", produces = MediaType.APPLICATION_JSON_VALUE)
    @HystrixCommand(fallbackMethod="fallbackMethod", 
    		ignoreExceptions = UserNotFoundException.class,
    		commandProperties = { 
    				@HystrixProperty(name="execution.isolation.thread.timeoutInMilliseconds", value="5000"),
    				@HystrixProperty(name = "execution.isolation.strategy", value = "SEMAPHORE")
    	})
	public Set<Movie> findRecommendationsForUser(@PathVariable String user) throws UserNotFoundException {
       
    	RequestContextHolder.currentRequestAttributes();
        Member member = membershipRepository.findMember(user);
        if(member == null)
            throw new UserNotFoundException();
        if ( member.age < 17 ) {
        	for(Movie mov : kidRecommendations) {
        		mov.setMemberHostName(member.getHostName());
        	}
        	return kidRecommendations ;
        } else {
        	for(Movie mov : adultRecommendations) {
        		mov.setMemberHostName(member.getHostName());
        	}
        	return adultRecommendations;
        }
    	
    	/* Member member = restTemplate.getForObject("http://ec2-52-66-30-2.ap-south-1.compute.amazonaws.com:8000/api/member/{user}", Member.class, user);
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
        	*/
        
        //return member.age < 17 ? kidRecommendations : adultRecommendations;
    }
    
    
     Set<Movie> fallbackMethod(String user) {
    	 System.out.println("Fallback Method");
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
	
	
	public Member(String user, Integer age, String hostName) {
		super();
		this.user = user;
		this.age = age;
		this.hostName = hostName;
	}
	String hostName;
	public String getHostName() {
		return hostName;
	}
	public void setHostName(String hostName) {
		this.hostName = hostName;
	}
	
	
}

class UserNotFoundException extends Exception {}