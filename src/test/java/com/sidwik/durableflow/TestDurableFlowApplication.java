package com.sidwik.durableflow;

import org.springframework.boot.SpringApplication;

public class TestDurableFlowApplication {

	public static void main(String[] args) {
		SpringApplication.from(DurableFlowApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
