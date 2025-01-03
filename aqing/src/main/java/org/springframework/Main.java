package org.springframework;


import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class Main {
	public static void main(String[] args) {
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(Main.class);
		Main bean = context.getBean(Main.class);
		System.out.println(bean);
	}
}